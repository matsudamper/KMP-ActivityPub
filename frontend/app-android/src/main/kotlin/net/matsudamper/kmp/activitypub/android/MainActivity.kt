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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
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
    MaterialTheme {
        Box(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                entryProvider = entryProvider {
                    entry<Screen.Login> { HomeFeatureScreens.Login(navigator = navigator) }
                    entry<Screen.Home> { HomeFeatureScreens.Home(navigator = navigator) }
                    entry<Screen.Post> { HomeFeatureScreens.Post() }
                },
            )
        }
    }
}
