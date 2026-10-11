package net.matsudamper.kmp.activitypub.android.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ServerScreen(
    onDecide: (serverUrl: String) -> Unit,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) { ServerScreenViewModel(viewModelScope) }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, onDecide) {
        viewModel.eventHandler.collect(
            object : ServerScreenViewModel.Event {
                override fun decided(serverUrl: String) {
                    onDecide(serverUrl)
                }
            },
        )
    }

    ServerContent(uiState = uiState)
}

@Composable
internal fun ServerContent(
    uiState: ServerScreenUiState,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("サーバー", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("接続するサーバーの URL を入れる。")
            OutlinedTextField(
                value = uiState.serverUrl,
                onValueChange = uiState.listener::onServerUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("https://example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { uiState.listener.onClickConnect() }),
                isError = uiState.error != null,
            )
            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = uiState.listener::onClickConnect,
                enabled = uiState.connectButtonEnabled,
            ) {
                Text("接続")
            }
        }
    }
}
