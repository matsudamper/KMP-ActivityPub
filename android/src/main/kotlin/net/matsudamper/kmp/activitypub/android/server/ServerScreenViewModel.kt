package net.matsudamper.kmp.activitypub.android.server

import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender

internal class ServerScreenViewModel(
    private val viewModelScope: CoroutineScope,
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    private val listener = object : ServerScreenUiState.Listener {
        override fun onServerUrlChanged(text: String) {
            viewModelStateFlow.update { it.copy(serverUrl = text, error = null) }
        }

        override fun onClickConnect() {
            connect()
        }
    }

    val uiStateFlow: StateFlow<ServerScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.value = createUiState(viewModelState)
                }
            }
        }.asStateFlow()

    private fun connect() {
        val normalized = normalize(viewModelStateFlow.value.serverUrl)
        if (normalized == null) {
            viewModelStateFlow.update { it.copy(error = "https:// から始まるサーバーの URL を入れる（パスは付けない）") }
            return
        }
        viewModelScope.launch {
            events.send { it.decided(normalized) }
        }
    }

    /**
     * ドメインだけ入れられたら https を補う。パスを足して叩くため、パス・クエリ・フラグメントの付いた URL は受け付けない。
     *
     * http は受け付けない。Android は既定で平文の通信を止めるので、通しても繋がらない
     */
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

    private fun createUiState(state: ViewModelState): ServerScreenUiState = ServerScreenUiState(
        serverUrl = state.serverUrl,
        error = state.error,
        connectButtonEnabled = state.serverUrl.isNotBlank(),
        listener = listener,
    )

    private data class ViewModelState(
        val serverUrl: String = "",
        val error: String? = null,
    )

    interface Event {
        fun decided(serverUrl: String)
    }
}
