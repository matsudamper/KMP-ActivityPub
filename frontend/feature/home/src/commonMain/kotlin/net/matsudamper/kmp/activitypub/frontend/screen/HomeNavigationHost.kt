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
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginCheckFailedContent
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginGateStatusContent
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginGateUiState
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginGateViewModel
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginRequestContent
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostScreen
import net.matsudamper.kmp.activitypub.frontend.screen.profileedit.ProfileEditScreen
import net.matsudamper.kmp.activitypub.frontend.screen.settings.SettingsScreen
import net.matsudamper.kmp.activitypub.frontend.screen.user.UserProfileScreen
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeNavigationHost(
    backStack: NavBackStack<NavKey>,
    serverSettingUiState: @Composable () -> ServerSettingUiState?,
    modifier: Modifier = Modifier,
    otherScreenContent: @Composable (screen: Screen, navigator: Navigator) -> Unit = { _, _ -> },
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
                                otherScreenContent = otherScreenContent,
                            )
                        }

                        LoginGateUiState.Content.LoginRequired -> {
                            LoginRequestContent(uiState = loginGateUiState, navigator = navigator)
                        }

                        LoginGateUiState.Content.Loading -> {
                            LoginGateStatusContent(uiState = loginGateUiState)
                        }

                        is LoginGateUiState.Content.Error -> {
                            LoginCheckFailedContent(uiState = loginGateUiState, navigator = navigator)
                        }
                    }
                } else {
                    ScreenContent(
                        screen = screen,
                        navigator = navigator,
                        serverSettingUiState = serverSettingUiState,
                        otherScreenContent = otherScreenContent,
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
    otherScreenContent: @Composable (screen: Screen, navigator: Navigator) -> Unit,
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

        Screen.Settings -> {
            SettingsScreen(navigator = navigator)
        }

        Screen.ProfileEdit -> {
            ProfileEditScreen(navigator = navigator)
        }

        is Screen.UserProfile -> {
            UserProfileScreen(username = screen.username)
        }

        Screen.Admin,
        Screen.AdminAccounts,
        Screen.AdminAccountNew,
        -> {
            otherScreenContent(screen, navigator)
        }
    }
}
