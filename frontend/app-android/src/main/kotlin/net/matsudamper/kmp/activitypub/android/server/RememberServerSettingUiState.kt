package net.matsudamper.kmp.activitypub.android.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState

@Composable
internal fun rememberServerSettingUiState(
    serverSettingStore: ServerSettingStore,
    viewModel: ServerSettingViewModel = viewModel {
        ServerSettingViewModel(
            savedStateHandle = createSavedStateHandle(),
            serverSettingStore = serverSettingStore,
        )
    },
): ServerSettingUiState {
    val uiState by viewModel.uiStateFlow.collectAsState()
    return uiState
}
