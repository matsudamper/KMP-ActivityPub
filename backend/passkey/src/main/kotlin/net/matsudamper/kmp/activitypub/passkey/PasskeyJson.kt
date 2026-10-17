package net.matsudamper.kmp.activitypub.passkey

import kotlin.time.Duration
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * WebAuthn Level 3 の options の JSON 表現。Web の parseCreationOptionsFromJSON と
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

    private const val PUBLIC_KEY = "public-key"
}
