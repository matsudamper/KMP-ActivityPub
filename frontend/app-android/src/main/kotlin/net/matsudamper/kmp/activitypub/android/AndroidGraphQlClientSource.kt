package net.matsudamper.kmp.activitypub.android

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import net.matsudamper.kmp.activitypub.android.server.ServerSettingStore
import net.matsudamper.kmp.activitypub.android.session.BlockStoreSessionBackup
import net.matsudamper.kmp.activitypub.android.session.EncryptedSessionStore
import net.matsudamper.kmp.activitypub.android.session.SessionHttpInterceptor
import net.matsudamper.kmp.activitypub.android.session.SessionRepository
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClient
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClientSource
import okhttp3.OkHttpClient
import org.koin.core.annotation.Singleton

@Singleton
internal class AndroidGraphQlClientSource(
    context: Context,
    serverSettingStore: ServerSettingStore,
) : GraphQlClientSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionRepository = SessionRepository(
        local = EncryptedSessionStore(context.applicationContext),
        backup = BlockStoreSessionBackup(context),
        scope = scope,
        reportBackupFailure = { Log.w("SessionBackup", "セッションの保存・復元に失敗。次回起動またはセッション更新時に再試行する") },
    )
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val clientState: StateFlow<ClientState> =
        serverSettingStore.serverUrlFlow
            .distinctUntilChanged()
            .map { serverUrl ->
                if (serverUrl == null) {
                    ClientState.Loaded(client = null)
                } else {
                    ClientState.Loaded(
                        client = GraphQlClient.builder("$serverUrl/graphql")
                            .okHttpClient(httpClient)
                            .addHttpInterceptor(SessionHttpInterceptor(sessionRepository))
                            .build(),
                    )
                }
            }.stateIn(scope, SharingStarted.Eagerly, ClientState.Loading)

    override val client: StateFlow<ApolloClient?> =
        clientState
            .map { (it as? ClientState.Loaded)?.client }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun awaitLoadedClient(): ApolloClient? = clientState.filterIsInstance<ClientState.Loaded>().first().client

    private sealed interface ClientState {
        data object Loading : ClientState

        data class Loaded(
            val client: ApolloClient?,
        ) : ClientState
    }
}
