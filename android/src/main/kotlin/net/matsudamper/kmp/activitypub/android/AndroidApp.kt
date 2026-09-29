package net.matsudamper.kmp.activitypub.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import net.matsudamper.kmp.activitypub.android.server.ServerScreen
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen

/**
 * Android アプリの入口。管理画面は出さない
 */
@Composable
internal fun AndroidApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val serverUrlStore = remember(context) { ServerUrlStore(context) }
    var serverUrl by remember(serverUrlStore) { mutableStateOf(serverUrlStore.load()) }

    MaterialTheme {
        Box(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
            AndroidAppContent(
                serverUrl = serverUrl,
                onServerUrlDecide = { decided ->
                    serverUrlStore.save(decided)
                    serverUrl = decided
                },
            )
        }
    }
}

@Composable
private fun AndroidAppContent(
    serverUrl: String?,
    onServerUrlDecide: (String) -> Unit,
) {
    val context = LocalContext.current
    if (serverUrl == null) {
        ServerScreen(onDecide = onServerUrlDecide)
    } else {
        val api = remember(context, serverUrl) {
            UserApi(AndroidGraphQlClient.create(context = context, serverUrl = serverUrl))
        }
        HomeScreen(api = api)
    }
}
