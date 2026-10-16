package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginGate

object HomeFeatureWebScreens {
    @Composable
    fun LoginRequired(content: @Composable () -> Unit) {
        LoginGate(content = content)
    }
}
