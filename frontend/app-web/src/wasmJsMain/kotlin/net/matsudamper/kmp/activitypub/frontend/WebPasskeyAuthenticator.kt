package net.matsudamper.kmp.activitypub.frontend

import kotlin.js.Promise
import kotlinx.coroutines.await
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAuthenticator

internal class WebPasskeyAuthenticator : PasskeyAuthenticator {
    override val available: Boolean = isWebAuthnJsonAvailable()

    override suspend fun create(request: PasskeyAuthenticator.CreationRequest): PasskeyAuthenticator.Result<PasskeyAuthenticator.Creation> {
        val result = createCredential(optionsJson = request.optionsJson).await<JsCredentialResult>()
        val credentialJson = result.credentialJson
        return when {
            credentialJson != null -> PasskeyAuthenticator.Result.Success(PasskeyAuthenticator.Creation(credentialJson = credentialJson))

            // excludeCredentials に当たった。この端末には追加済み
            result.errorName == "InvalidStateError" -> PasskeyAuthenticator.Result.Failure("この端末のパスキーは追加済み")

            else -> result.toFailure()
        }
    }

    override suspend fun get(request: PasskeyAuthenticator.AssertionRequest): PasskeyAuthenticator.Result<PasskeyAuthenticator.Assertion> {
        val result = getCredential(optionsJson = request.optionsJson).await<JsCredentialResult>()
        val credentialJson = result.credentialJson
        return if (credentialJson != null) {
            PasskeyAuthenticator.Result.Success(PasskeyAuthenticator.Assertion(credentialJson = credentialJson))
        } else {
            result.toFailure()
        }
    }

    private fun JsCredentialResult.toFailure(): PasskeyAuthenticator.Result<Nothing> =
        when (errorName) {
            // 閉じた・タイムアウトした・使えるパスキーが無い、を WebAuthn は区別させない
            "NotAllowedError", "AbortError" -> PasskeyAuthenticator.Result.Cancelled

            else -> PasskeyAuthenticator.Result.Failure(errorMessage ?: "パスキーを使えなかった")
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
