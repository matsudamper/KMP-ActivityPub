package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostScreen

object HomeFeatureScreens {
    /**
     * @param api 接続先が未設定なら null
     * @param serverKey 接続先ごとに ViewModel を分けるキー。Activity の再生成で変わらない値にする
     */
    @Composable
    fun Login(
        navigator: Navigator,
        api: UserApi?,
        serverKey: String,
        serverSettingUiState: ServerSettingUiState?,
    ) {
        LoginScreen(
            navigator = navigator,
            api = api,
            serverKey = serverKey,
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
