package net.matsudamper.kmp.activitypub.android.server

import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState

internal class ServerSettingViewModel(
    private val viewModelScope: CoroutineScope,
    private val serverSettingStore: ServerSettingStore,
) {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    private val listener = object : ServerSettingUiState.Listener {
        override fun onClickChange() {
            viewModelStateFlow.update { state ->
                state.copy(dialog = DialogState(serverUrl = state.savedServerUrl.orEmpty(), invalidServerUrl = false))
            }
        }

        override fun onServerUrlChanged(text: String) {
            viewModelStateFlow.update { state ->
                state.copy(dialog = state.dialog?.copy(serverUrl = text, invalidServerUrl = false))
            }
        }

        override fun onClickSave() {
            save()
        }

        override fun onDismissDialog() {
            viewModelStateFlow.update { it.copy(dialog = null) }
        }
    }

    val uiStateFlow: StateFlow<ServerSettingUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.value = createUiState(viewModelState)
                }
            }
        }.asStateFlow()

    init {
        viewModelScope.launch {
            serverSettingStore.serverUrlFlow.collect { serverUrl ->
                viewModelStateFlow.update { it.copy(savedServerUrl = serverUrl) }
            }
        }
    }

    private fun save() {
        val dialog = viewModelStateFlow.value.dialog ?: return
        val normalized = normalize(dialog.serverUrl)
        if (normalized == null) {
            viewModelStateFlow.update { state ->
                state.copy(dialog = dialog.copy(invalidServerUrl = true))
            }
            return
        }
        viewModelScope.launch {
            try {
                serverSettingStore.save(normalized)
            } catch (_: IOException) {
                return@launch
            }
            viewModelStateFlow.update { it.copy(dialog = null) }
        }
    }

    private fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrEmpty() || uri.rawUserInfo != null) return null
        if (!(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")) return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        if (uri.port != -1 && uri.port !in 1..65535) return null

        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "https://${uri.host}$port"
    }

    private fun createUiState(state: ViewModelState): ServerSettingUiState = ServerSettingUiState(
        serverUrlText = state.savedServerUrl ?: "未設定",
        dialog = state.dialog?.let { dialog ->
            ServerSettingUiState.Dialog(
                serverUrl = dialog.serverUrl,
                serverUrlInputError = dialog.invalidServerUrl,
                saveButtonEnabled = dialog.serverUrl.isNotBlank(),
            )
        },
        listener = listener,
    )

    private data class ViewModelState(
        val savedServerUrl: String? = null,
        val dialog: DialogState? = null,
    )

    private data class DialogState(
        val serverUrl: String,
        val invalidServerUrl: Boolean,
    )
}
