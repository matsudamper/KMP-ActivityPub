package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState

object HomeFeatureScreens {
    @Composable
    fun NavigationHost(
        backStack: NavBackStack<NavKey>,
        serverSettingUiState: @Composable () -> ServerSettingUiState?,
        modifier: Modifier = Modifier,
    ) {
        ScreenNavigationHost(
            backStack = backStack,
            serverSettingUiState = serverSettingUiState,
            modifier = modifier,
        )
    }
}
