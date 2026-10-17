package net.matsudamper.kmp.activitypub.frontend.screen.logingate

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginDialog

/**
 * 開いた画面の上でログインさせ、通ったら URL を変えずにその画面を出す
 */
@Composable
internal actual fun LoginRequiredContent(
    uiState: LoginGateUiState,
    navigator: Navigator,
) {
    LoginGateStatusContent(uiState = uiState)
    LoginDialog(onLoggedIn = uiState.listener::onLoggedIn)
}
