package net.matsudamper.kmp.activitypub.frontend.screen.logingate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginDialog
import org.koin.compose.viewmodel.koinViewModel

/**
 * ログインが確かめられるまで [content] を出さない
 */
@Composable
internal fun LoginGate(content: @Composable () -> Unit) {
    val viewModel = koinViewModel<LoginGateViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()

    when (val state = uiState.content) {
        LoginGateUiState.Content.Screen -> content()

        LoginGateUiState.Content.Loading -> {
            GateSurface {
                CircularProgressIndicator()
            }
        }

        LoginGateUiState.Content.LoginDialog -> {
            GateSurface {}
            LoginDialog(onLoggedIn = uiState.listener::onLoggedIn)
        }

        is LoginGateUiState.Content.Error -> {
            GateSurface {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = uiState.listener::onClickRetry) {
                        Text("再読み込み")
                    }
                }
            }
        }
    }
}

@Composable
private fun GateSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
