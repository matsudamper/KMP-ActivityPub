package net.matsudamper.kmp.activitypub.frontend.wasm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.apollographql.apollo.ApolloClient
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClient
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClientSource
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

@Module
class WasmGraphQlModule {
    @Singleton
    fun graphQlClientSource(): GraphQlClientSource = WasmGraphQlClientSource()
}

private class WasmGraphQlClientSource : GraphQlClientSource {
    override val client: StateFlow<ApolloClient?> =
        MutableStateFlow(GraphQlClient.builder("/graphql").build())
}
