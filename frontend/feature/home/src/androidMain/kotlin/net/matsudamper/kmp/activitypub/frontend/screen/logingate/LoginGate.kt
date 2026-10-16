package net.matsudamper.kmp.activitypub.frontend.screen.logingate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import org.koin.compose.viewmodel.koinViewModel

/**
 * ログインしていなければログイン画面へ戻す
 */
@Composable
internal fun LoginGate(
    navigator: Navigator,
    content: @Composable () -> Unit,
) {
    val viewModel = koinViewModel<LoginGateViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()

    when (uiState.content) {
        LoginGateUiState.Content.Screen -> {
            content()
        }

        LoginGateUiState.Content.LoginRequired -> {
            LoginGateStatusContent(uiState = uiState)
            LaunchedEffect(navigator) {
                navigator.replaceAll(Screen.Login)
            }
        }

        LoginGateUiState.Content.Loading,
        is LoginGateUiState.Content.Error,
        -> {
            LoginGateStatusContent(uiState = uiState)
        }
    }
}
