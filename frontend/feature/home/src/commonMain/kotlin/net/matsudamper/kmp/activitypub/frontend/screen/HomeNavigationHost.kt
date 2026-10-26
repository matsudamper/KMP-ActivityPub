package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
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
) {
    val navigator = remember(backStack) { Navigator(backStack) }
    // 画面ごとに持つと、下タブを切り替えるたびにログインの確認からやり直しになり、確認の間は下タブごと消える
    val loginGateViewModel = koinViewModel<LoginGateViewModel>()
    val loginGateUiState by loginGateViewModel.uiStateFlow.collectAsState()
    val currentScreen = backStack.lastOrNull() as? Screen
    LaunchedEffect(currentScreen, loginGateUiState.listener) {
        if (currentScreen?.loginRequired == true) {
            loginGateUiState.listener.onLoginRequiredScreenShown()
        }
    }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {
            if (isHomeTabSwitch()) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                defaultTransitionSpec<NavKey>()(this)
            }
        },
        popTransitionSpec = {
            if (isHomeTabSwitch()) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                defaultPopTransitionSpec<NavKey>()(this)
            }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { key ->
            NavEntry(
                key = key,
                metadata = if (key is Screen.Home) HOME_TAB_METADATA else mapOf(),
            ) {
                val screen = key as Screen
                if (screen.loginRequired) {
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
                    )
                }
            }
        },
    )
}

private const val HOME_TAB_METADATA_KEY = "homeTab"
private val HOME_TAB_METADATA: Map<String, Any> = mapOf(HOME_TAB_METADATA_KEY to true)

/**
 * 下タブの切り替えは画面を積み替えているが、見た目は中身だけが変わる。
 * 画面遷移のアニメーションを付けると下タブまでフェードしてちらつく
 */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.isHomeTabSwitch(): Boolean {
    return initialState.isHomeTab() && targetState.isHomeTab()
}

private fun Scene<NavKey>.isHomeTab(): Boolean {
    return entries.lastOrNull()?.metadata?.containsKey(HOME_TAB_METADATA_KEY) == true
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

        is Screen.Home -> {
            HomeScreen(navigator = navigator, tab = screen.tab)
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
    }
}
