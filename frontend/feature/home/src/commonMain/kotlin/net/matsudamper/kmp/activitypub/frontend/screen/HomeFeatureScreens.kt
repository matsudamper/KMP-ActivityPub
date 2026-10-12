package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginScreen
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostScreen

object HomeFeatureScreens {
    /**
     * @param serverSetting 接続先を選べるアプリだけが渡す。ログインボタンの下に出る
     */
    @Composable
    fun Login(
        navigator: Navigator,
        serverSetting: (@Composable () -> Unit)?,
    ) {
        LoginScreen(navigator = navigator, serverSetting = serverSetting)
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
