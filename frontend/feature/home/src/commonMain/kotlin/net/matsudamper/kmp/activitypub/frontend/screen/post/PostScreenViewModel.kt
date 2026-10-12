package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.runtime.saveable.Saver
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
    savedState: ViewModelState,
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(savedState)

    val uiStateFlow: StateFlow<PostScreenUiState> =
        MutableStateFlow(
            PostScreenUiState(
                body = savedState.body,
                remainingLength = savedState.remainingLength(),
                remainingLengthOver = savedState.remainingLength() < 0,
                postButtonEnabled = savedState.postable(),
                listener = object : PostScreenUiState.Listener {
                    override fun onBodyChanged(text: String) {
                        viewModelStateFlow.update { it.copy(body = text.take(BODY_INPUT_MAX_LENGTH)) }
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

    data class ViewModelState(
        val body: String,
    )

    interface Event {
        fun navigateBack()
    }

    companion object {
        val InitialState = ViewModelState(body = "")

        /**
         * Mastodon の既定の投稿長。超えるとサーバーが弾く
         */
        private const val NOTE_MAX_LENGTH = 500

        /**
         * 本文は Activity の保存状態に入る。Binder の転送上限を超えるとクラッシュするので、
         * 投稿長を超えたと分かる程度の長さで入力を打ち切る
         */
        private const val BODY_INPUT_MAX_LENGTH = NOTE_MAX_LENGTH * 4

        /**
         * 画面回転などで作り直されても書きかけの本文を残す
         */
        fun saver(viewModelScope: CoroutineScope): Saver<PostScreenViewModel, String> = Saver(
            save = { viewModel -> viewModel.viewModelStateFlow.value.body },
            restore = { body ->
                PostScreenViewModel(
                    viewModelScope = viewModelScope,
                    savedState = ViewModelState(body = body),
                )
            },
        )
    }
}
