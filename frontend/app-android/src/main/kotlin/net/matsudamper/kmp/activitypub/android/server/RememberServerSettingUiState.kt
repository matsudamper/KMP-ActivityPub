package net.matsudamper.kmp.activitypub.android.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState

@Composable
internal fun rememberServerSettingUiState(
    serverSettingStore: ServerSettingStore,
): ServerSettingUiState {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope, serverSettingStore) {
        ServerSettingViewModel(viewModelScope = viewModelScope, serverSettingStore = serverSettingStore)
    }
    val uiState by viewModel.uiStateFlow.collectAsState()
    return uiState
}
