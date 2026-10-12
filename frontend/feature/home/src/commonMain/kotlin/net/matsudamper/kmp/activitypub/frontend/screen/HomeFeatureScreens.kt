package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginScreen

object HomeFeatureScreens {
    @Composable
    fun Login(navigator: Navigator) {
        LoginScreen(navigator = navigator)
    }

    @Composable
    fun Home() {
        HomeScreen()
    }
}
