package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState

object HomeFeatureScreens {
    @Composable
    fun Login(
        navigator: Navigator,
        serverSettingUiState: ServerSettingUiState?,
    ) {
        LoginScreen(
            navigator = navigator,
            serverSettingUiState = serverSettingUiState,
        )
    }
}
