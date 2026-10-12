package net.matsudamper.kmp.activitypub.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.map
import net.matsudamper.kmp.activitypub.android.server.ServerSettingStore
import net.matsudamper.kmp.activitypub.android.server.rememberServerSettingUiState
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
    val applicationContext = LocalContext.current.applicationContext
    val serverSettingStore = remember(applicationContext) { ServerSettingStore(applicationContext) }
    val savedServerUrl by remember(serverSettingStore) {
        serverSettingStore.serverUrlFlow.map { SavedServerUrl.Loaded(it) }
    }.collectAsState(initial = SavedServerUrl.Loading)

    MaterialTheme {
        Box(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
            when (val loaded = savedServerUrl) {
                SavedServerUrl.Loading -> Unit

                is SavedServerUrl.Loaded -> key(loaded.serverUrl) {
                    ScreenNavDisplay(
                        serverUrl = loaded.serverUrl,
                        serverSettingStore = serverSettingStore,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenNavDisplay(
    serverUrl: String?,
    serverSettingStore: ServerSettingStore,
) {
    val context = LocalContext.current
    val api = remember(context, serverUrl) {
        serverUrl?.let { UserApi(AndroidGraphQlClient.create(context = context, serverUrl = it)) }
    }
    val backStack = rememberScreenBackStack(startScreen = if (api == null) Screen.Login else Screen.Home)
    val navigator = remember(backStack) { BackStackNavigator(backStack) }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<Screen.Login> {
                HomeFeatureScreens.Login(
                    navigator = navigator,
                    api = api,
                    serverSettingUiState = rememberServerSettingUiState(serverSettingStore),
                )
            }
            // 接続先が無いとログインできないので、ここへは来ない
            entry<Screen.Home> { api?.let { HomeFeatureScreens.Home(navigator = navigator, api = it) } }
            entry<Screen.Post> { api?.let { HomeFeatureScreens.Post(navigator = navigator, api = it) } }
        },
    )
}

private sealed interface SavedServerUrl {
    data object Loading : SavedServerUrl

    data class Loaded(
        val serverUrl: String?,
    ) : SavedServerUrl
}
