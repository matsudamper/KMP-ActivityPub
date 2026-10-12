package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun PostScreen() {
    val viewModel = remember { PostScreenViewModel() }
    val uiState by viewModel.uiStateFlow.collectAsState()

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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = uiState.message,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}
