package net.matsudamper.kmp.activitypub.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.android.server.ServerScreen
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.ui.RetainedScreenState

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
                onClickChangeServer = {
                    serverUrlStore.clear()
                    serverUrl = null
                },
            )
        }
    }
}

@Composable
private fun AndroidAppContent(
    serverUrl: String?,
    onServerUrlDecide: (String) -> Unit,
    onClickChangeServer: () -> Unit,
) {
    val context = LocalContext.current
    if (serverUrl == null) {
        ServerScreen(onDecide = onServerUrlDecide)
    } else {
        val api = remember(context, serverUrl) {
            UserApi(AndroidGraphQlClient.create(context = context, serverUrl = serverUrl))
        }
        val parentScope = rememberCoroutineScope()
        val retainedScreenState = remember(parentScope, serverUrl) { RetainedScreenState(parentScope) }
        DisposableEffect(retainedScreenState) {
            onDispose { retainedScreenState.dispose() }
        }
        // 打ち間違えたホストや止まったサーバーを保存すると、ここから戻れないとアプリのデータを消すしかない
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = serverUrl,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onClickChangeServer) {
                    Text("接続先を変更")
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                HomeScreen(retainedScreenState = retainedScreenState, api = api)
            }
        }
    }
}
