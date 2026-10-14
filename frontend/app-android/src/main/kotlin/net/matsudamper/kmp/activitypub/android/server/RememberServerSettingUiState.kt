package net.matsudamper.kmp.activitypub.android.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun rememberServerSettingUiState(): ServerSettingUiState {
    val viewModel = koinViewModel<ServerSettingViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    return uiState
}
