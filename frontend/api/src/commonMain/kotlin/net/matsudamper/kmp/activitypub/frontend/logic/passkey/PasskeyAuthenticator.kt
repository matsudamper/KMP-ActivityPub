package net.matsudamper.kmp.activitypub.frontend.logic.passkey

/**
 * 端末のパスキーを作る・使う。バイト列はすべて base64url（パディング無し）で受け渡す
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

data class PasskeyCreationRequest(
    val challenge: String,
    val rpId: String,
    val userHandle: String,
    val username: String,
    /**
     * COSE Algorithms の値。先頭ほど優先する
     */
    val algorithms: List<Int>,
    val excludeCredentialIds: List<String>,
)

data class PasskeyCreation(
    val clientDataJson: String,
    val attestationObject: String,
)

data class PasskeyAssertionRequest(
    val challenge: String,
    val rpId: String,
)

data class PasskeyAssertion(
    val credentialId: String,
    val clientDataJson: String,
    val authenticatorData: String,
    val signature: String,
    val userHandle: String?,
)
