package net.matsudamper.kmp.activitypub.android.passkey

import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAssertion
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAssertionRequest
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAuthenticator
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAuthenticatorResult
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyCreation
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyCreationRequest
import org.koin.core.annotation.Singleton

/**
 * Credential Manager の要求と応答は WebAuthn の JSON 表現（バイト列は base64url）で受け渡す
 */
@Singleton
internal class AndroidPasskeyAuthenticator(
    private val activityHolder: ForegroundActivityHolder,
) : PasskeyAuthenticator {
    /**
     * Credential Manager のパスキーは Android 9 から
     */
    override val available: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    override suspend fun create(request: PasskeyCreationRequest): PasskeyAuthenticatorResult<PasskeyCreation> {
        val activity = activityHolder.current ?: return PasskeyAuthenticatorResult.Failure(NO_ACTIVITY_MESSAGE)
        val requestJson =
            buildJsonObject {
                put("challenge", request.challenge)
                putJsonObject("rp") {
                    put("id", request.rpId)
                    put("name", request.rpId)
                }
                putJsonObject("user") {
                    put("id", request.userHandle)
                    put("name", request.username)
                    put("displayName", request.username)
                }
                putJsonArray("pubKeyCredParams") {
                    request.algorithms.forEach { algorithm ->
                        addJsonObject {
                            put("type", "public-key")
                            put("alg", algorithm)
                        }
                    }
                }
                putJsonArray("excludeCredentials") {
                    request.excludeCredentialIds.forEach { id ->
                        addJsonObject {
                            put("type", "public-key")
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

        val response =
            try {
                CredentialManager.create(activity).createCredential(activity, CreatePublicKeyCredentialRequest(requestJson))
            } catch (_: CreateCredentialCancellationException) {
                return PasskeyAuthenticatorResult.Cancelled
            } catch (e: CreatePublicKeyCredentialDomException) {
                // excludeCredentials に当たった。この端末には追加済み
                if (e.domError is InvalidStateError) return PasskeyAuthenticatorResult.Failure("この端末のパスキーは追加済み")
                return PasskeyAuthenticatorResult.Failure(e.message ?: e.type)
            } catch (e: CreateCredentialException) {
                return PasskeyAuthenticatorResult.Failure(e.message ?: e.type)
            }
        if (response !is CreatePublicKeyCredentialResponse) return PasskeyAuthenticatorResult.Failure(UNEXPECTED_RESPONSE_MESSAGE)

        val attestation = responseOf(response.registrationResponseJson) ?: return PasskeyAuthenticatorResult.Failure(UNEXPECTED_RESPONSE_MESSAGE)
        val clientDataJson = attestation.string("clientDataJSON")
        val attestationObject = attestation.string("attestationObject")
        if (clientDataJson == null || attestationObject == null) return PasskeyAuthenticatorResult.Failure(UNEXPECTED_RESPONSE_MESSAGE)

        return PasskeyAuthenticatorResult.Success(PasskeyCreation(clientDataJson = clientDataJson, attestationObject = attestationObject))
    }

    override suspend fun get(request: PasskeyAssertionRequest): PasskeyAuthenticatorResult<PasskeyAssertion> {
        val activity = activityHolder.current ?: return PasskeyAuthenticatorResult.Failure(NO_ACTIVITY_MESSAGE)
        val requestJson =
            buildJsonObject {
                put("challenge", request.challenge)
                put("rpId", request.rpId)
                put("userVerification", "required")
                // 空にすると、端末にあるこの RP のパスキーから選ばせる
                putJsonArray("allowCredentials") {}
            }.toString()

        val response =
            try {
                CredentialManager.create(activity).getCredential(
                    activity,
                    GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson))),
                )
            } catch (_: GetCredentialCancellationException) {
                return PasskeyAuthenticatorResult.Cancelled
            } catch (_: NoCredentialException) {
                return PasskeyAuthenticatorResult.Failure("この端末に使えるパスキーが無い")
            } catch (e: GetCredentialException) {
                return PasskeyAuthenticatorResult.Failure(e.message ?: e.type)
            }
        val credential = response.credential as? PublicKeyCredential ?: return PasskeyAuthenticatorResult.Failure(UNEXPECTED_RESPONSE_MESSAGE)

        val json = parse(credential.authenticationResponseJson) ?: return PasskeyAuthenticatorResult.Failure(UNEXPECTED_RESPONSE_MESSAGE)
        val assertion = json["response"] as? JsonObject ?: return PasskeyAuthenticatorResult.Failure(UNEXPECTED_RESPONSE_MESSAGE)
        val credentialId = json.string("rawId")
        val clientDataJson = assertion.string("clientDataJSON")
        val authenticatorData = assertion.string("authenticatorData")
        val signature = assertion.string("signature")
        if (credentialId == null || clientDataJson == null || authenticatorData == null || signature == null) {
            return PasskeyAuthenticatorResult.Failure(UNEXPECTED_RESPONSE_MESSAGE)
        }

        return PasskeyAuthenticatorResult.Success(
            PasskeyAssertion(
                credentialId = credentialId,
                clientDataJson = clientDataJson,
                authenticatorData = authenticatorData,
                signature = signature,
                userHandle = assertion.string("userHandle"),
            ),
        )
    }

    private fun responseOf(json: String): JsonObject? = parse(json)?.get("response") as? JsonObject

    private fun parse(json: String): JsonObject? =
        try {
            Json.parseToJsonElement(json) as? JsonObject
        } catch (_: SerializationException) {
            null
        }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val NO_ACTIVITY_MESSAGE = "画面が開いていない"
        const val UNEXPECTED_RESPONSE_MESSAGE = "パスキーの応答を読めない"
    }
}
