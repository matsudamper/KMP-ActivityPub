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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 画面を出すまでの間に、画面の代わりに出すもの
 */
@Composable
internal fun LoginGateStatusContent(
    uiState: LoginGateUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val content = uiState.content) {
                LoginGateUiState.Content.Loading -> {
                    CircularProgressIndicator()
                }

                is LoginGateUiState.Content.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = content.message,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = uiState.listener::onClickRetry) {
                            Text("再読み込み")
                        }
                    }
                }

                LoginGateUiState.Content.LoginRequired,
                LoginGateUiState.Content.Screen,
                -> Unit
            }
        }
    }
}
