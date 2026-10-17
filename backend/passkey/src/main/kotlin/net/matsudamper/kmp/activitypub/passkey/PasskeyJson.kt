package net.matsudamper.kmp.activitypub.passkey

import java.util.Base64
import kotlin.time.Duration
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * WebAuthn Level 3 の JSON 表現。Web の parseCreationOptionsFromJSON / toJSON と
 * Android の Credential Manager がそのまま扱える形で、バイト列は base64url（パディング無し）
 */
object PasskeyJson {
    /**
     * PublicKeyCredentialCreationOptionsJSON
     */
    fun creationOptions(
        relyingParty: PasskeyRelyingParty,
        challenge: String,
        userHandle: String,
        username: String,
        algorithms: List<Long>,
        excludeCredentialIds: List<String>,
        timeout: Duration,
    ): String =
        buildJsonObject {
            putJsonObject("rp") {
                put("id", relyingParty.id)
                put("name", relyingParty.id)
            }
            putJsonObject("user") {
                put("id", userHandle)
                put("name", username)
                put("displayName", username)
            }
            put("challenge", challenge)
            putJsonArray("pubKeyCredParams") {
                algorithms.forEach { algorithm ->
                    addJsonObject {
                        put("type", PUBLIC_KEY)
                        put("alg", algorithm)
                    }
                }
            }
            put("timeout", timeout.inWholeMilliseconds)
            putJsonArray("excludeCredentials") {
                excludeCredentialIds.forEach { id ->
                    addJsonObject {
                        put("type", PUBLIC_KEY)
                        put("id", id)
                    }
                }
            }
            putJsonObject("authenticatorSelection") {
                put("residentKey", "required")
                put("requireResidentKey", true)
                put("userVerification", "required")
            }
            put("attestation", "none")
        }.toString()

    /**
     * PublicKeyCredentialRequestOptionsJSON。allowCredentials を空にして、端末にあるこの RP のパスキーから選ばせる
     */
    fun requestOptions(
        relyingParty: PasskeyRelyingParty,
        challenge: String,
        timeout: Duration,
    ): String =
        buildJsonObject {
            put("challenge", challenge)
            put("timeout", timeout.inWholeMilliseconds)
            put("rpId", relyingParty.id)
            putJsonArray("allowCredentials") {}
            put("userVerification", "required")
        }.toString()

    /**
     * RegistrationResponseJSON
     *
     * @throws PasskeyVerificationException
     */
    fun parseRegistrationResponse(json: String): PasskeyRegistrationResponse {
        val response = parseCredential(json).objectOf("response")
        return PasskeyRegistrationResponse(
            clientDataJson = response.bytesOf("clientDataJSON"),
            attestationObject = response.bytesOf("attestationObject"),
        )
    }

    /**
     * AuthenticationResponseJSON
     *
     * @throws PasskeyVerificationException
     */
    fun parseAuthenticationResponse(json: String): PasskeyAuthenticationResponse {
        val credential = parseCredential(json)
        val response = credential.objectOf("response")
        return PasskeyAuthenticationResponse(
            credentialId = credential.bytesOf("rawId"),
            clientDataJson = response.bytesOf("clientDataJSON"),
            authenticatorData = response.bytesOf("authenticatorData"),
            signature = response.bytesOf("signature"),
            userHandle = response.bytesOrNullOf("userHandle"),
        )
    }

    private fun parseCredential(json: String): JsonObject {
        val credential =
            try {
                Json.parseToJsonElement(json) as? JsonObject
            } catch (e: SerializationException) {
                throw PasskeyVerificationException("パスキーの応答を JSON として読めない", e)
            } ?: throw PasskeyVerificationException("パスキーの応答がオブジェクトではない")
        if (credential.stringOrNullOf("type") != PUBLIC_KEY) throw PasskeyVerificationException("パスキーの応答の type が違う")
        return credential
    }

    private fun JsonObject.objectOf(key: String): JsonObject = this[key] as? JsonObject ?: throw PasskeyVerificationException("パスキーの応答に $key が無い")

    private fun JsonObject.stringOrNullOf(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.bytesOf(key: String): ByteArray = bytesOrNullOf(key) ?: throw PasskeyVerificationException("パスキーの応答に $key が無い")

    private fun JsonObject.bytesOrNullOf(key: String): ByteArray? {
        val value = stringOrNullOf(key) ?: return null
        return try {
            Base64.getUrlDecoder().decode(value)
        } catch (e: IllegalArgumentException) {
            throw PasskeyVerificationException("パスキーの応答の $key が base64url として読めない", e)
        }
    }

    private const val PUBLIC_KEY = "public-key"
}

class PasskeyRegistrationResponse(
    val clientDataJson: ByteArray,
    val attestationObject: ByteArray,
)

class PasskeyAuthenticationResponse(
    val credentialId: ByteArray,
    val clientDataJson: ByteArray,
    val authenticatorData: ByteArray,
    val signature: ByteArray,
    val userHandle: ByteArray?,
)
