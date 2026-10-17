package net.matsudamper.kmp.activitypub.passkey

import java.util.Base64
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationData
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.PublicKeyCredentialParameters
import com.webauthn4j.data.PublicKeyCredentialType
import com.webauthn4j.data.RegistrationData
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.authenticator.COSEKey
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.verifier.exception.VerificationException
import tools.jackson.core.JacksonException

/**
 * 受け取った応答を [parseRegistration] / [parseAuthentication] で読み、呼び出し側が challenge を
 * 発行済みのものと突き合わせて使い捨てにしてから、[verifyRegistration] / [verifyAuthentication] で検証する。
 *
 * attestation は求めない（"none"）。パスワードの代わりにするので、本人確認（UV）は必須にする
 */
class PasskeyVerifier(
    private val relyingParty: PasskeyRelyingParty,
) {
    private val objectConverter = ObjectConverter()
    private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter)
    private val origins = relyingParty.origins.map(Origin::create).toSet()

    /**
     * 登録で受け付ける署名方式の COSE の値。登録の要求に載せて、認証器にこの中から選ばせる
     */
    val supportedAlgorithms: List<Long> = SUPPORTED_ALGORITHMS.map { it.value }

    /**
     * @param credentialJson RegistrationResponseJSON
     * @throws PasskeyVerificationException
     */
    fun parseRegistration(credentialJson: String): PasskeyRegistration = PasskeyRegistration(convert { webAuthnManager.parseRegistrationResponseJSON(credentialJson) })

    /**
     * @throws PasskeyVerificationException
     */
    fun verifyRegistration(registration: PasskeyRegistration): RegisteredPasskey {
        val parameters =
            RegistrationParameters(
                serverProperty(registration.data.collectedClientData?.challenge?.value),
                SUPPORTED_ALGORITHMS.map { PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, it) },
                true,
                true,
            )
        val verified = convert { webAuthnManager.verify(registration.data, parameters) }
        val authenticatorData = verified.attestationObject?.authenticatorData ?: throw PasskeyVerificationException("attestationObject が無い")
        val credential = authenticatorData.attestedCredentialData ?: throw PasskeyVerificationException("登録なのに credential が入っていない")

        val publicKey = objectConverter.cborConverter.writeValueAsBytes(credential.coseKey)
        if (publicKey.size > MAX_PUBLIC_KEY_BYTES) throw PasskeyVerificationException("公開鍵が大きすぎる")
        return RegisteredPasskey(
            credentialId = credential.credentialId,
            publicKey = publicKey,
            signCount = authenticatorData.signCount,
        )
    }

    /**
     * @param credentialJson AuthenticationResponseJSON
     * @throws PasskeyVerificationException
     */
    fun parseAuthentication(credentialJson: String): PasskeyAuthentication = PasskeyAuthentication(convert { webAuthnManager.parseAuthenticationResponseJSON(credentialJson) })

    /**
     * @param publicKey 登録時の [RegisteredPasskey.publicKey]
     * @param storedSignCount 前回までに受け取った署名回数。増えていなければ通さない（どちらも 0 なら比べない）
     * @return 今回の署名回数。次回の [storedSignCount] に渡す
     * @throws PasskeyVerificationException
     */
    fun verifyAuthentication(
        authentication: PasskeyAuthentication,
        publicKey: ByteArray,
        storedSignCount: Long,
    ): Long {
        val coseKey = convert { objectConverter.cborConverter.readValue(publicKey, COSEKey::class.java) } ?: throw PasskeyVerificationException("保存した公開鍵を読めない")
        val credentialRecord =
            CredentialRecordImpl(
                NoneAttestationStatement(),
                true,
                null,
                null,
                storedSignCount,
                AttestedCredentialData(AAGUID.NULL, authentication.credentialId, coseKey),
                null,
                null,
                null,
                null,
            )
        val parameters =
            AuthenticationParameters(
                serverProperty(authentication.data.collectedClientData?.challenge?.value),
                credentialRecord,
                null,
                true,
                true,
            )
        val verified = convert { webAuthnManager.verify(authentication.data, parameters) }
        return verified.authenticatorData?.signCount ?: throw PasskeyVerificationException("authenticatorData が無い")
    }

    private fun serverProperty(challenge: ByteArray?): ServerProperty =
        ServerProperty(origins, relyingParty.id, DefaultChallenge(challenge ?: throw PasskeyVerificationException("challenge が無い")))

    /**
     * 受け取った値は例外の文言に入りうるので、ログに出す文言には型名だけを使う
     */
    private fun <T> convert(block: () -> T): T =
        try {
            block()
        } catch (e: DataConversionException) {
            throw PasskeyVerificationException("読めない: ${e::class.simpleName}", e)
        } catch (e: VerificationException) {
            throw PasskeyVerificationException("検証に通らない: ${e::class.simpleName}", e)
        } catch (e: JacksonException) {
            throw PasskeyVerificationException("読めない: ${e::class.simpleName}", e)
        } catch (e: IllegalArgumentException) {
            throw PasskeyVerificationException("読めない: ${e::class.simpleName}", e)
        }

    private companion object {
        val SUPPORTED_ALGORITHMS: List<COSEAlgorithmIdentifier> =
            listOf(COSEAlgorithmIdentifier.ES256, COSEAlgorithmIdentifier.EdDSA, COSEAlgorithmIdentifier.RS256)

        /**
         * 対応する方式で最も大きい RSA 4096 bit でも 600 バイトに収まる。
         * 使わない項目を詰め込んだ鍵を保存させ、ログインのたびに読ませないため
         */
        const val MAX_PUBLIC_KEY_BYTES = 1024
    }
}

class PasskeyRegistration internal constructor(
    internal val data: RegistrationData,
) {
    /**
     * base64url（パディング無し）
     */
    val challenge: String = encodeBase64Url(data.collectedClientData?.challenge?.value ?: throw PasskeyVerificationException("challenge が無い"))
}

class PasskeyAuthentication internal constructor(
    internal val data: AuthenticationData,
) {
    val credentialId: ByteArray = data.credentialId ?: throw PasskeyVerificationException("credential ID が無い")
    val userHandle: ByteArray? = data.userHandle

    /**
     * base64url（パディング無し）
     */
    val challenge: String = encodeBase64Url(data.collectedClientData?.challenge?.value ?: throw PasskeyVerificationException("challenge が無い"))
}

/**
 * @param id RP ID。パスキーはこのドメインに紐づき、後から変えると登録済みのものが使えなくなる
 * @param origins clientDataJSON の origin として受け付ける値。ブラウザなら `https://example.com` の形
 */
class PasskeyRelyingParty(
    val id: String,
    val origins: Set<String>,
)

class RegisteredPasskey(
    val credentialId: ByteArray,
    /**
     * COSE_Key のバイト列。ログインの検証で [PasskeyVerifier.verifyAuthentication] に渡す
     */
    val publicKey: ByteArray,
    val signCount: Long,
)

class PasskeyVerificationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

private fun encodeBase64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
