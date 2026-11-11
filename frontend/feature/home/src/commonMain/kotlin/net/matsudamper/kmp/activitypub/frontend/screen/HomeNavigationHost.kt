package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.screen.admin.AdminScreen
import net.matsudamper.kmp.activitypub.frontend.screen.followinggroups.FollowingGroupsScreen
import net.matsudamper.kmp.activitypub.frontend.screen.followlist.FollowListKind
import net.matsudamper.kmp.activitypub.frontend.screen.followlist.FollowListScreen
import net.matsudamper.kmp.activitypub.frontend.screen.home.COLUMNS_LAYOUT_MIN_WIDTH
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.screen.image.ImageViewerScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginCheckFailedContent
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginGateStatusContent
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginGateUiState
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginGateViewModel
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginRequestContent
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostNavigationIcon
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostScreen
import net.matsudamper.kmp.activitypub.frontend.screen.postdetail.PostDetailScreen
import net.matsudamper.kmp.activitypub.frontend.screen.profileedit.ProfileEditScreen
import net.matsudamper.kmp.activitypub.frontend.screen.security.SecurityScreen
import net.matsudamper.kmp.activitypub.frontend.screen.settings.SettingsScreen
import net.matsudamper.kmp.activitypub.frontend.screen.user.UserProfileScreen
import net.matsudamper.kmp.activitypub.frontend.ui.clearPostBodySelectionOnPress
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeNavigationHost(
    backStack: NavBackStack<NavKey>,
    serverSettingUiState: @Composable () -> ServerSettingUiState?,
    modifier: Modifier = Modifier,
) {
    val navigator = remember(backStack) { Navigator(backStack) }
    BoxWithConstraints(modifier = modifier) {
        val openAsDialog = maxWidth >= COLUMNS_LAYOUT_MIN_WIDTH
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize().clearPostBodySelectionOnPress(),
            onBack = navigator::back,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            sceneStrategies = listOf(DialogSceneStrategy()),
            popTransitionSpec = { fadeIn() togetherWith fadeOut() },
            predictivePopTransitionSpec = { fadeIn() togetherWith fadeOut() },
            entryProvider = { key ->
                NavEntry(
                    key,
                    contentKey = if (key is Screen.Home) Screen.Home.Timeline.path else (key as Screen).path,
                    metadata = when {
                        key is Screen.Image -> ImageViewerDialogMetadata
                        key is Screen.UserProfile && openAsDialog -> UserProfileDialogMetadata
                        key is Screen.PostDetail && openAsDialog -> PostDetailDialogMetadata
                        else -> mapOf()
                    },
                ) { screenKey ->
                    val screen = screenKey as Screen
                    if (screen is Screen.UserProfile && openAsDialog) {
                        UserProfileDialogFrame {
                            ScreenEntryContent(
                                screen = screen,
                                navigator = navigator,
                                serverSettingUiState = serverSettingUiState,
                            )
                        }
                    } else if (screen is Screen.PostDetail && openAsDialog) {
                        PostDetailDialogFrame {
                            ScreenEntryContent(
                                screen = screen,
                                navigator = navigator,
                                serverSettingUiState = serverSettingUiState,
                            )
                        }
                    } else {
                        ScreenEntryContent(
                            screen = screen,
                            navigator = navigator,
                            serverSettingUiState = serverSettingUiState,
                        )
                    }
                }
            },
        )
    }
}

@Composable
internal fun UserProfileDialogFrame(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .widthIn(max = USER_PROFILE_DIALOG_MAX_WIDTH)
            .fillMaxWidth()
            .fillMaxHeight(0.9f),
        shape = MaterialTheme.shapes.large,
    ) {
        content()
    }
}

/**
 * 画像を大きく並べられるよう、プロフィールより広くする
 */
@Composable
internal fun PostDetailDialogFrame(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .widthIn(max = POST_DETAIL_DIALOG_MAX_WIDTH)
            .fillMaxWidth(0.9f)
            .fillMaxHeight(0.9f),
        shape = MaterialTheme.shapes.large,
    ) {
        content()
    }
}

@Composable
private fun ScreenEntryContent(
    screen: Screen,
    navigator: Navigator,
    serverSettingUiState: @Composable () -> ServerSettingUiState?,
) {
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
            PostScreen(navigationIcon = PostNavigationIcon.Back, onNavigateBack = navigator::back)
        }

        Screen.Settings -> {
            SettingsScreen(navigator = navigator)
        }

        Screen.Security -> {
            SecurityScreen(navigator = navigator)
        }

        Screen.FollowingGroups -> {
            FollowingGroupsScreen(navigator = navigator)
        }

        Screen.Admin -> {
            AdminScreen(navigator = navigator)
        }

        Screen.Following -> {
            FollowListScreen(navigator = navigator, kind = FollowListKind.Following)
        }

        Screen.Followers -> {
            FollowListScreen(navigator = navigator, kind = FollowListKind.Followers)
        }

        Screen.ProfileEdit -> {
            ProfileEditScreen(navigator = navigator)
        }

        is Screen.UserProfile -> {
            UserProfileScreen(username = screen.username, navigator = navigator)
        }

        is Screen.PostDetail -> {
            PostDetailScreen(postId = screen.postId, navigator = navigator)
        }

        is Screen.Image -> {
            ImageViewerScreen(imageUrls = screen.urls, initialIndex = screen.initialIndex, navigator = navigator)
        }
    }
}

private val USER_PROFILE_DIALOG_MAX_WIDTH = 600.dp

private val POST_DETAIL_DIALOG_MAX_WIDTH = 1000.dp

private val PostDetailDialogMetadata: Map<String, Any> =
    DialogSceneStrategy.dialog(DialogProperties(usePlatformDefaultWidth = false))

private val UserProfileDialogMetadata: Map<String, Any> =
    DialogSceneStrategy.dialog(DialogProperties(usePlatformDefaultWidth = false))

private val ImageViewerDialogMetadata: Map<String, Any> =
    DialogSceneStrategy.dialog(DialogProperties(usePlatformDefaultWidth = false))
