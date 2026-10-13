package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostScreen

object HomeFeatureScreens {
    /**
     * @param apiFlow 接続先が未設定の間は null を流す
     */
    @Composable
    fun Login(
        navigator: Navigator,
        apiFlow: Flow<UserApi?>,
        serverSettingUiState: ServerSettingUiState?,
    ) {
        LoginScreen(
            navigator = navigator,
            apiFlow = apiFlow,
            serverSettingUiState = serverSettingUiState,
        )
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
