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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import net.matsudamper.kmp.activitypub.android.server.ServerScreen
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.navigation.BackStackNavigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.navigation.rememberScreenBackStack
import net.matsudamper.kmp.activitypub.frontend.screen.HomeFeatureScreens

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
    if (serverUrl == null) {
        ServerScreen(onDecide = onServerUrlDecide)
    } else {
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
                key(serverUrl) {
                    ScreenNavDisplay(serverUrl = serverUrl)
                }
            }
        }
    }
}

@Composable
private fun ScreenNavDisplay(serverUrl: String) {
    val context = LocalContext.current
    val api = remember(context, serverUrl) {
        UserApi(AndroidGraphQlClient.create(context = context, serverUrl = serverUrl))
    }
    val backStack = rememberScreenBackStack(startScreen = Screen.Home)
    val navigator = remember(backStack) { BackStackNavigator(backStack) }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<Screen.Login> { HomeFeatureScreens.Login(navigator = navigator, api = api, serverSettingUiState = null) }
            entry<Screen.Home> { HomeFeatureScreens.Home(navigator = navigator, api = api) }
            entry<Screen.Post> { HomeFeatureScreens.Post(navigator = navigator, api = api) }
        },
    )
}
