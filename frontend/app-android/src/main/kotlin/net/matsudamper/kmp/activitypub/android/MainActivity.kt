package net.matsudamper.kmp.activitypub.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import net.matsudamper.kmp.activitypub.android.server.ServerSettingStore
import net.matsudamper.kmp.activitypub.android.server.rememberServerSettingUiState
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.navigation.rememberScreenBackStack
import net.matsudamper.kmp.activitypub.frontend.screen.HomeFeatureScreens

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidApp()
        }
    }
}

@Composable
private fun AndroidApp(modifier: Modifier = Modifier) {
    val backStack = rememberScreenBackStack(startScreen = Screen.Login)
    val navigator = remember(backStack) { Navigator(backStack) }
    val applicationContext = LocalContext.current.applicationContext
    val serverSettingStore = remember(applicationContext) { ServerSettingStore(applicationContext) }
    val serverUrl by serverSettingStore.serverUrlFlow.collectAsState(initial = null)
    val api = remember(applicationContext, serverUrl) {
        serverUrl?.let { UserApi(AndroidGraphQlClient.create(context = applicationContext, serverUrl = it)) }
    }
    MaterialTheme {
        Box(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                entryProvider = entryProvider {
                    entry<Screen.Login> {
                        val serverSettingUiState = rememberServerSettingUiState(serverSettingStore)
                        // 接続先が変わったら前の接続先に向けて動いている処理ごと作り直す。
                        // UserApi は Activity の再生成で別インスタンスになり、入力の復元が外れるので URL をキーにする
                        key(serverUrl) {
                            HomeFeatureScreens.Login(
                                navigator = navigator,
                                api = api,
                                serverSettingUiState = serverSettingUiState,
                            )
                        }
                    }
                    entry<Screen.Home> { HomeFeatureScreens.Home(navigator = navigator) }
                    entry<Screen.Post> { HomeFeatureScreens.Post(navigator = navigator) }
                },
            )
        }
    }
}
