package net.matsudamper.kmp.activitypub.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import net.matsudamper.kmp.activitypub.frontend.navigation.BackStackNavigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginScreen
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostScreen

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
private fun AndroidApp() {
    val navigator = remember { BackStackNavigator(startScreen = Screen.Login) }
    BackHandler(enabled = navigator.backEnabled) {
        navigator.back()
    }
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            when (navigator.currentScreen) {
                Screen.Login -> LoginScreen(navigator = navigator)
                Screen.Home -> HomeScreen(navigator = navigator)
                Screen.Post -> PostScreen(navigator = navigator)
            }
        }
    }
}
