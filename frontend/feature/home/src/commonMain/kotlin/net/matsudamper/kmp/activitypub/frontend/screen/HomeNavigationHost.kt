package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginGateStatusContent
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginGateUiState
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginGateViewModel
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginRequestContent
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostScreen
import net.matsudamper.kmp.activitypub.frontend.screen.user.UserProfileScreen
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeNavigationHost(
    backStack: NavBackStack<NavKey>,
    serverSettingUiState: @Composable () -> ServerSettingUiState?,
    modifier: Modifier = Modifier,
) {
    val navigator = remember(backStack) { Navigator(backStack) }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { key ->
            NavEntry(key) {
                val screen = key as Screen
                if (screen.loginRequired) {
                    val loginGateViewModel = koinViewModel<LoginGateViewModel>()
                    val loginGateUiState by loginGateViewModel.uiStateFlow.collectAsState()
                    when (loginGateUiState.content) {
                        LoginGateUiState.Content.Screen -> {
                            ScreenContent(
                                screen = screen,
                                navigator = navigator,
                                serverSettingUiState = serverSettingUiState,
                            )
                        }

                        LoginGateUiState.Content.LoginRequired -> {
                            LoginRequestContent(uiState = loginGateUiState, navigator = navigator)
                        }

                        LoginGateUiState.Content.Loading,
                        is LoginGateUiState.Content.Error,
                        -> {
                            LoginGateStatusContent(uiState = loginGateUiState)
                        }
                    }
                } else {
                    ScreenContent(
                        screen = screen,
                        navigator = navigator,
                        serverSettingUiState = serverSettingUiState,
                    )
                }
            }
        },
    )
}

@Composable
private fun ScreenContent(
    screen: Screen,
    navigator: Navigator,
    serverSettingUiState: @Composable () -> ServerSettingUiState?,
) {
    when (screen) {
        Screen.Login -> {
            LoginScreen(
                navigator = navigator,
                serverSettingUiState = serverSettingUiState(),
            )
        }

        Screen.Home -> {
            HomeScreen(navigator = navigator)
        }

        Screen.Post -> {
            PostScreen(navigator = navigator)
        }

        is Screen.UserProfile -> {
            UserProfileScreen(username = screen.username)
        }
    }
}
