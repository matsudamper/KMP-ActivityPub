package net.matsudamper.kmp.activitypub.frontend.screen.logingate

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator

/**
 * ログインしていないと分かったときに、画面の代わりに出すもの。扱いはプラットフォームごとに違う
 */
@Composable
internal expect fun LoginRequiredContent(
    uiState: LoginGateUiState,
    navigator: Navigator,
)
