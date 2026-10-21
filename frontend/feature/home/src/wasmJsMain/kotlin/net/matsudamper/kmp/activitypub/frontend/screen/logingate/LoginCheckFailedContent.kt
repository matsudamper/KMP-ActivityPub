package net.matsudamper.kmp.activitypub.frontend.screen.logingate

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator

/**
 * 開いた画面の URL を保つため、ログイン画面へは移さず再読み込みだけさせる
 */
@Composable
internal actual fun LoginCheckFailedContent(
    uiState: LoginGateUiState,
    navigator: Navigator,
) {
    LoginGateStatusContent(uiState = uiState)
}
