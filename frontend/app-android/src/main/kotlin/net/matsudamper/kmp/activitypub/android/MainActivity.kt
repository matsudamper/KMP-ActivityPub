package net.matsudamper.kmp.activitypub.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import net.matsudamper.kmp.activitypub.android.passkey.ForegroundActivityHolder
import net.matsudamper.kmp.activitypub.android.server.rememberServerSettingUiState
import net.matsudamper.kmp.activitypub.frontend.logic.theme.ThemeMode
import net.matsudamper.kmp.activitypub.frontend.logic.theme.ThemeSettingStore
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.navigation.rememberScreenBackStack
import net.matsudamper.kmp.activitypub.frontend.screen.HomeNavigationHost
import net.matsudamper.kmp.activitypub.frontend.screen.theme.AppTheme
import net.matsudamper.kmp.activitypub.frontend.screen.theme.isDarkTheme
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.plugin.module.dsl.koinConfiguration

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
    KoinApplication(configuration = koinConfiguration<AndroidKoinApp>()) {
        AndroidNavigation(modifier = modifier)
    }
}

@Composable
private fun AndroidNavigation(modifier: Modifier = Modifier) {
    val activityHolder = koinInject<ForegroundActivityHolder>()
    val activity = LocalActivity.current
    DisposableEffect(activityHolder, activity) {
        if (activity != null) activityHolder.attach(activity)
        onDispose {
            if (activity != null) activityHolder.detach(activity)
        }
    }
    val backStack = rememberScreenBackStack(startScreens = listOf(Screen.Home.Timeline))
    val navigator = remember(backStack) { Navigator(backStack) }
    BackHandler(enabled = navigator.homeTabBackEnabled, onBack = navigator::back)
    val themeSettingStore = koinInject<ThemeSettingStore>()
    val themeMode by themeSettingStore.themeModeFlow.collectAsState(initial = ThemeMode.System)
    val darkTheme = themeMode.isDarkTheme()
    SystemBarStyleEffect(darkTheme = darkTheme)
    AppTheme(darkTheme = darkTheme) {
        // 各画面が上下のバーの裏まで自分の色で塗り、システムバーの分は画面側で避ける
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding(),
        ) {
            HomeNavigationHost(
                backStack = backStack,
                serverSettingUiState = { rememberServerSettingUiState() },
            )
        }
    }
}

/**
 * enableEdgeToEdge の既定は端末のダークモードでアイコン色を決めるので、アプリでテーマを固定すると背景とアイコンが同じ色になる
 */
@Composable
private fun SystemBarStyleEffect(darkTheme: Boolean) {
    val activity = LocalActivity.current
    SideEffect {
        if (activity is ComponentActivity) {
            val systemBarStyle =
                if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
            activity.enableEdgeToEdge(
                statusBarStyle = systemBarStyle,
                navigationBarStyle = systemBarStyle,
            )
        }
    }
}
