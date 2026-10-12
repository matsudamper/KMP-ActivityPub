package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator

@Composable
fun PostScreen(
    navigator: Navigator,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) { PostScreenViewModel(viewModelScope = viewModelScope) }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : PostScreenViewModel.Event {
                override fun navigateBack() {
                    navigator.back()
                }
            },
        )
    }

    PostContent(uiState = uiState)
}

@Composable
internal fun PostContent(
    uiState: PostScreenUiState,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = uiState.listener::onClickBack) {
                    Text("戻る")
                }
                Text(
                    text = "投稿",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = uiState.body,
                    onValueChange = uiState.listener::onBodyChanged,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    placeholder = { Text("今なにしてる？") },
                    isError = uiState.remainingLengthOver,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = uiState.remainingLength.toString(),
                        modifier = Modifier.weight(1f),
                        color = if (uiState.remainingLengthOver) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Button(
                        onClick = uiState.listener::onClickPost,
                        enabled = uiState.postButtonEnabled,
                    ) {
                        Text("投稿")
                    }
                }
            }
        }
    }
}
