package net.matsudamper.kmp.activitypub.frontend

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClient
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.navigation.rememberScreenBackStack
import net.matsudamper.kmp.activitypub.frontend.screen.HomeFeatureScreens

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        App()
    }
}

@Composable
private fun App() {
    val backStack = rememberScreenBackStack(startScreen = Screen.Login)
    val navigator = remember(backStack) { Navigator(backStack) }
    val api = remember { UserApi(GraphQlClient.apollo) }
    MaterialTheme {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<Screen.Login> {
                    HomeFeatureScreens.Login(
                        navigator = navigator,
                        api = api,
                        serverSettingUiState = null,
                    )
                }
                entry<Screen.Home> { HomeFeatureScreens.Home(navigator = navigator) }
                entry<Screen.Post> { HomeFeatureScreens.Post(navigator = navigator) }
            },
        )
    }
}
