package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostScreen

object HomeFeatureScreens {
    @Composable
    fun Login(
        navigator: Navigator,
        serverSettingUiState: ServerSettingUiState?,
    ) {
        LoginScreen(navigator = navigator, serverSettingUiState = serverSettingUiState)
    }

    @Composable
    fun Home(navigator: Navigator) {
        HomeScreen(navigator = navigator)
    }

    @Composable
    fun Post(navigator: Navigator) {
        PostScreen(navigator = navigator)
    }
}
