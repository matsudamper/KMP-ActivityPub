package net.matsudamper.kmp.activitypub.android

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import net.matsudamper.kmp.activitypub.android.server.ServerSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClient
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClientSource
import okhttp3.OkHttpClient
import org.koin.core.annotation.Singleton

@Singleton
internal class AndroidGraphQlClientSource(
    context: Context,
    serverSettingStore: ServerSettingStore,
) : GraphQlClientSource {
    private val okHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .cookieJar(PersistentCookieJar(CookieBlockStore(context)))
            .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val client: StateFlow<ApolloClient?> =
        serverSettingStore.serverUrlFlow
            .distinctUntilChanged()
            .map { serverUrl ->
                if (serverUrl == null) {
                    null
                } else {
                    GraphQlClient
                        .builder("$serverUrl/graphql")
                        .okHttpClient(okHttpClient)
                        .build()
                }
            }.stateIn(scope, SharingStarted.Eagerly, null)
}
