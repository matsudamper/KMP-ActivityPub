package net.matsudamper.kmp.activitypub.frontend.logic.passkey

/**
 * 端末のパスキーを作る・使う。サーバーが返した WebAuthn の JSON をそのまま認証器に渡し、認証器の応答も JSON のまま返す
 */
interface PasskeyAuthenticator {
    /**
     * false の端末ではパスキーの操作を出さない
     */
    val available: Boolean

    suspend fun create(request: CreationRequest): Result<Creation>

    suspend fun get(request: AssertionRequest): Result<Assertion>

    sealed interface Result<out T> {
        data class Success<T>(
            val value: T,
        ) : Result<T>

        /**
         * ユーザーが閉じた。タイムアウトや、該当するパスキーが無いときもこれになる
         */
        data object Cancelled : Result<Nothing>

        data class Failure(
            val message: String,
        ) : Result<Nothing>
    }

    /**
     * @param optionsJson PublicKeyCredentialCreationOptionsJSON
     */
    data class CreationRequest(
        val optionsJson: String,
    )

    /**
     * @param credentialJson RegistrationResponseJSON
     */
    data class Creation(
        val credentialJson: String,
    )

    /**
     * @param optionsJson PublicKeyCredentialRequestOptionsJSON
     */
    data class AssertionRequest(
        val optionsJson: String,
    )

    /**
     * @param credentialJson AuthenticationResponseJSON
     */
    data class Assertion(
        val credentialJson: String,
    )
}
