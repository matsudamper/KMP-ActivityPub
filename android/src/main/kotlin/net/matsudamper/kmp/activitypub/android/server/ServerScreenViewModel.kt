package net.matsudamper.kmp.activitypub.android.server

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
            viewModelStateFlow.update { it.copy(error = "https:// から始まる URL を入れる") }
            return
        }
        viewModelScope.launch {
            events.send { it.decided(normalized) }
        }
    }

    /**
     * ドメインだけ入れられたら https を補う。末尾の `/` は落とす。パスを足して叩くため
     */
    private fun normalize(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null

        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        if (!withScheme.startsWith("https://") && !withScheme.startsWith("http://")) return null
        if (withScheme.substringAfter("://").isEmpty()) return null

        return withScheme
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
