package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostScreen

object HomeFeatureScreens {
    /**
     * @param api 接続先が決まっていなければ null。その間はログインできない
     */
    @Composable
    fun Login(
        navigator: Navigator,
        api: UserApi?,
        serverSettingUiState: ServerSettingUiState?,
    ) {
        // 接続先が変わったら前の接続先に向けて動いている処理ごと作り直す
        key(api) {
            LoginScreen(navigator = navigator, api = api, serverSettingUiState = serverSettingUiState)
        }
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
