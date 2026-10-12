package net.matsudamper.kmp.activitypub.frontend

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.navigation.rememberBackStackNavigator
import net.matsudamper.kmp.activitypub.frontend.screen.HomeFeatureScreens

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        App()
    }
}

@Composable
private fun App() {
    val navigator = rememberBackStackNavigator(startScreen = Screen.Login)
    MaterialTheme {
        navigator.CurrentScreen { screen ->
            when (screen) {
                Screen.Login -> HomeFeatureScreens.Login(navigator = navigator)
                Screen.Home -> HomeFeatureScreens.Home(navigator = navigator)
                Screen.Post -> HomeFeatureScreens.Post(navigator = navigator)
            }
        }
    }
}
