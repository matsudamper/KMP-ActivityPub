package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginGate
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostScreen

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

    /**
     * ログインしていなければ、その場でログインのダイアログを出し、通ったら [content] を出す
     */
    @Composable
    fun LoginRequired(content: @Composable () -> Unit) {
        LoginGate(content = content)
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
