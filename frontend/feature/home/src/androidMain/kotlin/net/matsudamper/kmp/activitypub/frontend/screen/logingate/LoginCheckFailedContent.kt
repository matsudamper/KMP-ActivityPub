package net.matsudamper.kmp.activitypub.frontend.screen.logingate

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen

/**
 * 接続先を直せるのはログイン画面だけなので、確認に失敗してもログイン画面へ移れるようにする
 */
@Composable
internal actual fun LoginCheckFailedContent(
    uiState: LoginGateUiState,
    navigator: Navigator,
) {
    LoginGateStatusContent(
        uiState = uiState,
        onClickOpenLogin = { navigator.replaceAll(Screen.Login) },
    )
}
