package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginGate

object HomeFeatureAndroidScreens {
    @Composable
    fun LoginRequired(
        navigator: Navigator,
        content: @Composable () -> Unit,
    ) {
        LoginGate(navigator = navigator, content = content)
    }
}
