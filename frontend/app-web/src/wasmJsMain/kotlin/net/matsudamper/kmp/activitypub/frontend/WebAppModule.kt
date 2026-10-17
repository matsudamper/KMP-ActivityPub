package net.matsudamper.kmp.activitypub.frontend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.apollographql.apollo.ApolloClient
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClient
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClientSource
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyApi
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAuthenticator
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

@Module
class WebAppModule {
    @Singleton
    fun graphQlClientSource(): GraphQlClientSource = WebGraphQlClientSource()

    @Singleton
    fun userApi(clients: GraphQlClientSource): UserApi = UserApi(clients)

    @Singleton
    fun passkeyApi(clients: GraphQlClientSource): PasskeyApi = PasskeyApi(clients)

    @Singleton
    fun passkeyAuthenticator(): PasskeyAuthenticator = WebPasskeyAuthenticator()
}

private class WebGraphQlClientSource : GraphQlClientSource {
    override val client: StateFlow<ApolloClient?> =
        MutableStateFlow(GraphQlClient.builder("/graphql").build())

    override suspend fun awaitLoadedClient(): ApolloClient? = client.value
}
