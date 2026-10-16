package net.matsudamper.kmp.activitypub.android

import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClientSource
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyApi
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAssertion
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAssertionRequest
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAuthenticator
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAuthenticatorResult
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyCreation
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyCreationRequest
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

@Module
@ComponentScan("net.matsudamper.kmp.activitypub.android")
class AndroidAppModule {
    @Singleton
    fun userApi(clients: GraphQlClientSource): UserApi = UserApi(clients)

    @Singleton
    fun passkeyApi(clients: GraphQlClientSource): PasskeyApi = PasskeyApi(clients)

    /**
     * Credential Manager に繋ぐまでは、パスキーの操作を出さない
     */
    @Singleton
    fun passkeyAuthenticator(): PasskeyAuthenticator = UnavailablePasskeyAuthenticator
}

private object UnavailablePasskeyAuthenticator : PasskeyAuthenticator {
    override val available: Boolean = false

    override suspend fun create(request: PasskeyCreationRequest): PasskeyAuthenticatorResult<PasskeyCreation> =
        PasskeyAuthenticatorResult.Failure("この端末ではパスキーを使えない")

    override suspend fun get(request: PasskeyAssertionRequest): PasskeyAuthenticatorResult<PasskeyAssertion> =
        PasskeyAuthenticatorResult.Failure("この端末ではパスキーを使えない")
}
