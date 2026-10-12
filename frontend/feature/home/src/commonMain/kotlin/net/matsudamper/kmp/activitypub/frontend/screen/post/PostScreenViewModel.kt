package net.matsudamper.kmp.activitypub.frontend.screen.post

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender

/**
 * サーバーとはまだ繋いでいない。投稿は常に成功したことにする
 */
internal class PostScreenViewModel(
    private val viewModelScope: CoroutineScope,
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    val uiStateFlow: StateFlow<PostScreenUiState> =
        MutableStateFlow(
            PostScreenUiState(
                body = "",
                remainingLength = NOTE_MAX_LENGTH,
                remainingLengthOver = false,
                postButtonEnabled = false,
                listener = object : PostScreenUiState.Listener {
                    override fun onBodyChanged(text: String) {
                        viewModelStateFlow.update { it.copy(body = text) }
                    }

                    override fun onClickPost() {
                        post()
                    }

                    override fun onClickBack() {
                        viewModelScope.launch {
                            events.send { it.navigateBack() }
                        }
                    }
                },
            ),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    val remainingLength = viewModelState.remainingLength()
                    uiStateFlow.update { uiState ->
                        uiState.copy(
                            body = viewModelState.body,
                            remainingLength = remainingLength,
                            remainingLengthOver = remainingLength < 0,
                            postButtonEnabled = viewModelState.postable(),
                        )
                    }
                }
            }
        }.asStateFlow()

    private fun post() {
        if (viewModelStateFlow.value.postable().not()) return

        viewModelScope.launch {
            events.send { it.navigateBack() }
        }
    }

    private fun ViewModelState.postable(): Boolean = body.isNotBlank() && remainingLength() >= 0

    // 書いた人にとっての文字数に合わせてコードポイントで数える
    private fun ViewModelState.remainingLength(): Int = NOTE_MAX_LENGTH - body.trim().count { !it.isLowSurrogate() }

    private data class ViewModelState(
        val body: String = "",
    )

    interface Event {
        fun navigateBack()
    }

    private companion object {
        /**
         * Mastodon の既定の投稿長。超えるとサーバーが弾く
         */
        const val NOTE_MAX_LENGTH = 500
    }
}
