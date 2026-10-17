package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginGate
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostScreen

object HomeFeatureWebScreens {
    @Composable
    fun Home(navigator: Navigator) {
        LoginGate {
            HomeScreen(navigator = navigator)
        }
    }

    @Composable
    fun Post(navigator: Navigator) {
        LoginGate {
            PostScreen(navigator = navigator)
        }
    }
}
