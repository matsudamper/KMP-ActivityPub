package net.matsudamper.kmp.activitypub.frontend.logic.passkey

/**
 * 端末のパスキーを作る・使う。サーバーが返した WebAuthn の JSON をそのまま認証器に渡し、認証器の応答も JSON のまま返す
 */
interface PasskeyAuthenticator {
    /**
     * false の端末ではパスキーの操作を出さない
     */
    val available: Boolean

    suspend fun create(request: PasskeyCreationRequest): PasskeyAuthenticatorResult<PasskeyCreation>

    suspend fun get(request: PasskeyAssertionRequest): PasskeyAuthenticatorResult<PasskeyAssertion>
}

sealed interface PasskeyAuthenticatorResult<out T> {
    data class Success<T>(
        val value: T,
    ) : PasskeyAuthenticatorResult<T>

    /**
     * ユーザーが閉じた。タイムアウトや、該当するパスキーが無いときもこれになる
     */
    data object Cancelled : PasskeyAuthenticatorResult<Nothing>

    data class Failure(
        val message: String,
    ) : PasskeyAuthenticatorResult<Nothing>
}

/**
 * @param optionsJson PublicKeyCredentialCreationOptionsJSON
 */
data class PasskeyCreationRequest(
    val optionsJson: String,
)

/**
 * @param credentialJson RegistrationResponseJSON
 */
data class PasskeyCreation(
    val credentialJson: String,
)

/**
 * @param optionsJson PublicKeyCredentialRequestOptionsJSON
 */
data class PasskeyAssertionRequest(
    val optionsJson: String,
)

/**
 * @param credentialJson AuthenticationResponseJSON
 */
data class PasskeyAssertion(
    val credentialJson: String,
)
