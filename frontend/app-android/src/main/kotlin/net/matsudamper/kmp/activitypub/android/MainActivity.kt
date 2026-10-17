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
import androidx.compose.ui.Modifier
import net.matsudamper.kmp.activitypub.android.server.rememberServerSettingUiState
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.navigation.rememberScreenBackStack
import net.matsudamper.kmp.activitypub.frontend.screen.HomeFeatureScreens
import org.koin.compose.KoinApplication
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
    val backStack = rememberScreenBackStack(startScreens = listOf(Screen.Login))
    MaterialTheme {
        Box(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
            HomeFeatureScreens.NavigationHost(
                backStack = backStack,
                serverSettingUiState = { rememberServerSettingUiState() },
            )
        }
    }
}
