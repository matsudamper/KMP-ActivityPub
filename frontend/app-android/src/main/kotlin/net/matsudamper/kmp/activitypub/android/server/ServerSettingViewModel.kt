package net.matsudamper.kmp.activitypub.android.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class ServerSettingViewModel(
    savedStateHandle: SavedStateHandle,
    private val serverSettingStore: ServerSettingStore,
) : ViewModel() {
    /**
     * null ならダイアログは閉じている
     */
    private val dialogServerUrlFlow: MutableStateFlow<String?> =
        savedStateHandle.getMutableStateFlow(KEY_DIALOG_SERVER_URL, null)
    private val invalidServerUrlFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val savedServerUrlFlow: StateFlow<String?> = serverSettingStore.serverUrlFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    private val listener = object : ServerSettingUiState.Listener {
        override fun onClickChange() {
            dialogServerUrlFlow.value = savedServerUrlFlow.value.orEmpty()
            invalidServerUrlFlow.value = false
        }

        override fun onServerUrlChanged(text: String) {
            if (dialogServerUrlFlow.value == null) return
            dialogServerUrlFlow.value = text
            invalidServerUrlFlow.value = false
        }

        override fun onClickSave() {
            save()
        }

        override fun onDismissDialog() {
            dialogServerUrlFlow.value = null
        }
    }

    val uiStateFlow: StateFlow<ServerSettingUiState> =
        combine(
            savedServerUrlFlow,
            dialogServerUrlFlow,
            invalidServerUrlFlow,
        ) { savedServerUrl, dialogServerUrl, invalidServerUrl ->
            createUiState(
                savedServerUrl = savedServerUrl,
                dialogServerUrl = dialogServerUrl,
                invalidServerUrl = invalidServerUrl,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = createUiState(
                savedServerUrl = savedServerUrlFlow.value,
                dialogServerUrl = dialogServerUrlFlow.value,
                invalidServerUrl = invalidServerUrlFlow.value,
            ),
        )

    private fun save() {
        val dialogServerUrl = dialogServerUrlFlow.value ?: return
        val normalized = normalize(dialogServerUrl)
        if (normalized == null) {
            invalidServerUrlFlow.value = true
            return
        }
        viewModelScope.launch {
            try {
                serverSettingStore.save(normalized)
            } catch (_: IOException) {
                return@launch
            }
            dialogServerUrlFlow.value = null
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

    private fun createUiState(
        savedServerUrl: String?,
        dialogServerUrl: String?,
        invalidServerUrl: Boolean,
    ): ServerSettingUiState = ServerSettingUiState(
        serverUrlText = savedServerUrl ?: "未設定",
        dialog = dialogServerUrl?.let { serverUrl ->
            ServerSettingUiState.Dialog(
                serverUrl = serverUrl,
                serverUrlInputError = invalidServerUrl,
                saveButtonEnabled = serverUrl.isNotBlank(),
            )
        },
        listener = listener,
    )

    private companion object {
        const val KEY_DIALOG_SERVER_URL = "dialogServerUrl"
    }
}
