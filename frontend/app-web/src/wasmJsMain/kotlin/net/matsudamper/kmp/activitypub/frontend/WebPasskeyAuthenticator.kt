package net.matsudamper.kmp.activitypub.frontend

import kotlin.js.Promise
import kotlinx.coroutines.await
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAssertion
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAssertionRequest
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAuthenticator
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAuthenticatorResult
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyCreation
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyCreationRequest

internal class WebPasskeyAuthenticator : PasskeyAuthenticator {
    override val available: Boolean = isWebAuthnJsonAvailable()

    override suspend fun create(request: PasskeyCreationRequest): PasskeyAuthenticatorResult<PasskeyCreation> {
        val result = createCredential(optionsJson = request.optionsJson).await<JsCredentialResult>()
        val credentialJson = result.credentialJson
        return when {
            credentialJson != null -> PasskeyAuthenticatorResult.Success(PasskeyCreation(credentialJson = credentialJson))

            // excludeCredentials に当たった。この端末には追加済み
            result.errorName == "InvalidStateError" -> PasskeyAuthenticatorResult.Failure("この端末のパスキーは追加済み")

            else -> result.toFailure()
        }
    }

    override suspend fun get(request: PasskeyAssertionRequest): PasskeyAuthenticatorResult<PasskeyAssertion> {
        val result = getCredential(optionsJson = request.optionsJson).await<JsCredentialResult>()
        val credentialJson = result.credentialJson
        return if (credentialJson != null) {
            PasskeyAuthenticatorResult.Success(PasskeyAssertion(credentialJson = credentialJson))
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
 * 成功なら credentialJson が、失敗なら errorName と errorMessage が入る
 */
private external interface JsCredentialResult : JsAny {
    val credentialJson: String?
    val errorName: String?
    val errorMessage: String?
}

private fun isWebAuthnJsonAvailable(): Boolean =
    js(
        """typeof window.PublicKeyCredential !== 'undefined' && !!navigator.credentials &&
            typeof PublicKeyCredential.parseCreationOptionsFromJSON === 'function' &&
            typeof PublicKeyCredential.parseRequestOptionsFromJSON === 'function'""",
    )

private fun createCredential(optionsJson: String): Promise<JsCredentialResult> =
    js(
        """(async () => {
            try {
                const publicKey = PublicKeyCredential.parseCreationOptionsFromJSON(JSON.parse(optionsJson));
                const credential = await navigator.credentials.create({ publicKey });
                return { credentialJson: JSON.stringify(credential.toJSON()) };
            } catch (e) {
                return { errorName: String(e?.name ?? 'Error'), errorMessage: String(e?.message ?? e) };
            }
        })()""",
    )

private fun getCredential(optionsJson: String): Promise<JsCredentialResult> =
    js(
        """(async () => {
            try {
                const publicKey = PublicKeyCredential.parseRequestOptionsFromJSON(JSON.parse(optionsJson));
                const credential = await navigator.credentials.get({ publicKey });
                return { credentialJson: JSON.stringify(credential.toJSON()) };
            } catch (e) {
                return { errorName: String(e?.name ?? 'Error'), errorMessage: String(e?.message ?? e) };
            }
        })()""",
    )
