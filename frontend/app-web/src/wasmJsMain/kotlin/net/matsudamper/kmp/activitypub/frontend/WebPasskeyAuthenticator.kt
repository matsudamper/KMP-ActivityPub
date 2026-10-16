package net.matsudamper.kmp.activitypub.frontend

import kotlin.js.Promise
import kotlin.js.toJsArray
import kotlin.js.toJsNumber
import kotlin.js.toJsString
import kotlinx.coroutines.await
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAssertion
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAssertionRequest
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAuthenticator
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAuthenticatorResult
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyCreation
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyCreationRequest

/**
 * navigator.credentials を呼ぶ。バイト列と base64url の変換は JS の側で済ませる
 */
internal class WebPasskeyAuthenticator : PasskeyAuthenticator {
    override val available: Boolean = isWebAuthnAvailable()

    override suspend fun create(request: PasskeyCreationRequest): PasskeyAuthenticatorResult<PasskeyCreation> {
        val result =
            createCredential(
                helpers = base64UrlHelpers,
                challenge = request.challenge,
                rpId = request.rpId,
                userHandle = request.userHandle,
                username = request.username,
                algorithms = request.algorithms.map { it.toJsNumber() }.toJsArray(),
                excludeCredentialIds = request.excludeCredentialIds.map { it.toJsString() }.toJsArray(),
            ).await<JsCredentialResult>()

        val clientDataJson = result.clientDataJson
        val attestationObject = result.attestationObject
        return when {
            clientDataJson != null && attestationObject != null -> {
                PasskeyAuthenticatorResult.Success(PasskeyCreation(clientDataJson = clientDataJson, attestationObject = attestationObject))
            }

            // excludeCredentials に当たった。この端末には追加済み
            result.errorName == "InvalidStateError" -> {
                PasskeyAuthenticatorResult.Failure("この端末のパスキーは追加済み")
            }

            else -> {
                result.toFailure()
            }
        }
    }

    override suspend fun get(request: PasskeyAssertionRequest): PasskeyAuthenticatorResult<PasskeyAssertion> {
        val result =
            getCredential(
                helpers = base64UrlHelpers,
                challenge = request.challenge,
                rpId = request.rpId,
            ).await<JsCredentialResult>()

        val credentialId = result.credentialId
        val clientDataJson = result.clientDataJson
        val authenticatorData = result.authenticatorData
        val signature = result.signature
        return if (credentialId != null && clientDataJson != null && authenticatorData != null && signature != null) {
            PasskeyAuthenticatorResult.Success(
                PasskeyAssertion(
                    credentialId = credentialId,
                    clientDataJson = clientDataJson,
                    authenticatorData = authenticatorData,
                    signature = signature,
                    userHandle = result.userHandle,
                ),
            )
        } else {
            result.toFailure()
        }
    }

    private fun JsCredentialResult.toFailure(): PasskeyAuthenticatorResult<Nothing> =
        when (errorName) {
            // 閉じた・タイムアウトした・使えるパスキーが無い、を WebAuthn は区別させない
            "NotAllowedError", "AbortError" -> PasskeyAuthenticatorResult.Cancelled

            else -> PasskeyAuthenticatorResult.Failure(errorMessage ?: "パスキーを使えなかった")
        }
}

/**
 * 成功ならエラー以外が、失敗なら errorName と errorMessage が入る
 */
private external interface JsCredentialResult : JsAny {
    val credentialId: String?
    val clientDataJson: String?
    val attestationObject: String?
    val authenticatorData: String?
    val signature: String?
    val userHandle: String?
    val errorName: String?
    val errorMessage: String?
}

private fun isWebAuthnAvailable(): Boolean = js("typeof window.PublicKeyCredential !== 'undefined' && !!navigator.credentials")

private val base64UrlHelpers: JsAny =
    js(
        """({
            decode: (value) => {
                const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
                return Uint8Array.from(atob(base64 + '='.repeat((4 - base64.length % 4) % 4)), (c) => c.charCodeAt(0));
            },
            encode: (buffer) => {
                const bytes = new Uint8Array(buffer);
                let binary = '';
                for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
                return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
            },
        })""",
    )

private fun createCredential(
    helpers: JsAny,
    challenge: String,
    rpId: String,
    userHandle: String,
    username: String,
    algorithms: JsArray<JsNumber>,
    excludeCredentialIds: JsArray<JsString>,
): Promise<JsCredentialResult> =
    js(
        """(async () => {
            try {
                const credential = await navigator.credentials.create({
                    publicKey: {
                        challenge: helpers.decode(challenge),
                        rp: { id: rpId, name: rpId },
                        user: { id: helpers.decode(userHandle), name: username, displayName: username },
                        pubKeyCredParams: Array.from(algorithms, (alg) => ({ type: 'public-key', alg: alg })),
                        excludeCredentials: Array.from(excludeCredentialIds, (id) => ({ type: 'public-key', id: helpers.decode(id) })),
                        authenticatorSelection: { residentKey: 'required', requireResidentKey: true, userVerification: 'required' },
                        attestation: 'none',
                    },
                });
                return {
                    clientDataJson: helpers.encode(credential.response.clientDataJSON),
                    attestationObject: helpers.encode(credential.response.attestationObject),
                };
            } catch (e) {
                return { errorName: String(e?.name ?? 'Error'), errorMessage: String(e?.message ?? e) };
            }
        })()""",
    )

private fun getCredential(
    helpers: JsAny,
    challenge: String,
    rpId: String,
): Promise<JsCredentialResult> =
    js(
        """(async () => {
            try {
                const credential = await navigator.credentials.get({
                    publicKey: {
                        challenge: helpers.decode(challenge),
                        rpId: rpId,
                        userVerification: 'required',
                    },
                });
                const response = credential.response;
                return {
                    credentialId: helpers.encode(credential.rawId),
                    clientDataJson: helpers.encode(response.clientDataJSON),
                    authenticatorData: helpers.encode(response.authenticatorData),
                    signature: helpers.encode(response.signature),
                    userHandle: response.userHandle ? helpers.encode(response.userHandle) : null,
                };
            } catch (e) {
                return { errorName: String(e?.name ?? 'Error'), errorMessage: String(e?.message ?? e) };
            }
        })()""",
    )
