package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostScreen

object HomeFeatureScreens {
    @Composable
    fun Login(
        navigator: Navigator,
        api: UserApi,
        serverSettingUiState: ServerSettingUiState?,
    ) {
        LoginScreen(navigator = navigator, api = api, serverSettingUiState = serverSettingUiState)
    }

    @Composable
    fun Home(
        navigator: Navigator,
        api: UserApi,
    ) {
        HomeScreen(navigator = navigator, api = api)
    }

    @Composable
    fun Post(
        navigator: Navigator,
        api: UserApi,
    ) {
        PostScreen(navigator = navigator, api = api)
    }
}
