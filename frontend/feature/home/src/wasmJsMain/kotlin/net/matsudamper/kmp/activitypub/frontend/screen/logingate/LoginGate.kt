package net.matsudamper.kmp.activitypub.frontend.screen.logingate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginDialog
import org.koin.compose.viewmodel.koinViewModel

/**
 * ログインしていなければ開いた画面の上でログインさせ、URL を変えずに [content] を出す
 */
@Composable
internal fun LoginGate(content: @Composable () -> Unit) {
    val viewModel = koinViewModel<LoginGateViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()

    when (uiState.content) {
        LoginGateUiState.Content.Screen -> {
            content()
        }

        LoginGateUiState.Content.LoginRequired -> {
            LoginGateStatusContent(uiState = uiState)
            LoginDialog(onLoggedIn = uiState.listener::onLoggedIn)
        }

        LoginGateUiState.Content.Loading,
        is LoginGateUiState.Content.Error,
        -> {
            LoginGateStatusContent(uiState = uiState)
        }
    }
}
