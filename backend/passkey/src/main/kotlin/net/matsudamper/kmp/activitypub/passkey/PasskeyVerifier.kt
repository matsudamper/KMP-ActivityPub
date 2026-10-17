package net.matsudamper.kmp.activitypub.passkey

import java.security.MessageDigest

/**
 * WebAuthn Level 3 の 7.1 / 7.2 の検証のうち、challenge の照合と保存した鍵の出し入れ以外を行う。
 * challenge は呼び出し側が [PasskeyClientData.challenge] を発行済みのものと突き合わせ、使い捨てにする。
 *
 * attestation は求めない（"none"）。認証器の機種を問わないので、届いた attestation statement は検証しない
 */
class PasskeyVerifier(
    private val relyingParty: PasskeyRelyingParty,
) {
    /**
     * 登録で受け付ける署名方式の COSE の値。登録の要求に載せて、認証器にこの中から選ばせる
     */
    val supportedAlgorithms: List<Long> = CoseAlgorithm.entries.map { it.coseValue }

    private val rpIdHash: ByteArray = MessageDigest.getInstance("SHA-256").digest(relyingParty.id.encodeToByteArray())

    /**
     * @throws PasskeyVerificationException
     */
    fun verifyRegistration(
        clientData: PasskeyClientData,
        attestationObject: ByteArray,
    ): RegisteredPasskey {
        verifyClientData(clientData, expectedType = TYPE_CREATE)

        val reader = CborReader(attestationObject, startIndex = 0)
        val attestation = reader.read() as? Map<*, *> ?: throw PasskeyVerificationException("attestationObject が map ではない")
        if (reader.position != attestationObject.size) throw PasskeyVerificationException("attestationObject の後ろに余分なバイトがある")
        val authenticatorDataBytes = attestation["authData"] as? ByteArray ?: throw PasskeyVerificationException("authData が無い")

        val authenticatorData = AuthenticatorData.parse(authenticatorDataBytes)
        verifyAuthenticatorData(authenticatorData)
        val credential = authenticatorData.attestedCredential ?: throw PasskeyVerificationException("登録なのに credential が入っていない")

        if (credential.publicKey.size > MAX_PUBLIC_KEY_BYTES) throw PasskeyVerificationException("公開鍵が大きすぎる")
        // 組み立てられない鍵や対応しない方式は、保存する前にここで落とす
        CosePublicKey.parse(credential.publicKey)

        return RegisteredPasskey(
            credentialId = credential.credentialId,
            publicKey = credential.publicKey,
            signCount = authenticatorData.signCount,
        )
    }

    /**
     * @param publicKey 登録時の [RegisteredPasskey.publicKey]
     * @param storedSignCount 前回までに受け取った署名回数
     * @return 今回の署名回数。次回の [storedSignCount] に渡す
     * @throws PasskeyVerificationException
     */
    fun verifyAuthentication(
        clientData: PasskeyClientData,
        authenticatorData: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray,
        storedSignCount: Long,
    ): Long {
        verifyClientData(clientData, expectedType = TYPE_GET)

        val parsed = AuthenticatorData.parse(authenticatorData)
        verifyAuthenticatorData(parsed)

        if (!CosePublicKey.parse(publicKey).verify(authenticatorData + clientData.hash(), signature)) {
            throw PasskeyVerificationException("署名が合わない")
        }

        // 回数を数えない認証器（同期されるパスキーなど）は常に 0 を返す。
        // どちらかが 0 なら比べない。増えていなければ複製された認証器の疑いがある
        if (parsed.signCount != 0L || storedSignCount != 0L) {
            if (parsed.signCount <= storedSignCount) throw PasskeyVerificationException("署名回数が増えていない")
        }

        return parsed.signCount
    }

    private fun verifyClientData(
        clientData: PasskeyClientData,
        expectedType: String,
    ) {
        if (clientData.type != expectedType) throw PasskeyVerificationException("clientDataJSON の type が違う")
        if (clientData.origin !in relyingParty.origins) throw PasskeyVerificationException("許可していない origin")
        // iframe の中から呼ばれる使い方はしない
        if (clientData.crossOrigin) throw PasskeyVerificationException("crossOrigin で呼ばれた")
    }

    private fun verifyAuthenticatorData(authenticatorData: AuthenticatorData) {
        if (!MessageDigest.isEqual(authenticatorData.rpIdHash, rpIdHash)) throw PasskeyVerificationException("RP ID が違う")
        if (!authenticatorData.userPresent) throw PasskeyVerificationException("ユーザーの操作が無い")
        // パスワードの代わりにするので、生体認証や PIN による本人確認を必須にする
        if (!authenticatorData.userVerified) throw PasskeyVerificationException("本人確認がされていない")
    }

    private companion object {
        const val TYPE_CREATE = "webauthn.create"
        const val TYPE_GET = "webauthn.get"

        /**
         * 対応する方式で最も大きい RSA 4096 bit でも 600 バイトに収まる。
         * 使わない項目を詰め込んだ鍵を保存させ、ログインのたびに読ませないため
         */
        const val MAX_PUBLIC_KEY_BYTES = 1024
    }
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
