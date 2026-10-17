package net.matsudamper.kmp.activitypub.frontend.screen.logingate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen

/**
 * ログイン画面へ戻す
 */
@Composable
internal actual fun LoginRequestContent(
    uiState: LoginGateUiState,
    navigator: Navigator,
) {
    LoginGateStatusContent(uiState = uiState)
    LaunchedEffect(navigator) {
        navigator.replaceAll(Screen.Login)
    }
}
