package net.matsudamper.kmp.activitypub.frontend.screen.login

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.koin.compose.viewmodel.koinViewModel

/**
 * 閉じると開いている画面を見られないまま残るので、ログインが通るまで閉じられない
 */
@Composable
internal fun LoginDialog(onLoggedIn: () -> Unit) {
    val viewModel = koinViewModel<LoginScreenViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    val currentOnLoggedIn by rememberUpdatedState(onLoggedIn)

    LaunchedEffect(viewModel.eventHandler) {
        viewModel.eventHandler.collect(
            object : LoginScreenViewModel.Event {
                override fun loggedIn() {
                    currentOnLoggedIn()
                }
            },
        )
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) {
            LoginForm(
                uiState = uiState,
                error = uiState.error,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}
