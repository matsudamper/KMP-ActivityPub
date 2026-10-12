package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.runtime.saveable.Saver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNoteResult

internal class PostScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: UserApi,
    savedState: ViewModelState,
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(savedState)

    val uiStateFlow: StateFlow<PostScreenUiState> =
        MutableStateFlow(
            createUiState(
                viewModelState = savedState,
                listener = object : PostScreenUiState.Listener {
                    override fun onBodyChanged(text: String) {
                        viewModelStateFlow.update { it.copy(body = text, error = null) }
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
                    uiStateFlow.update { uiState ->
                        createUiState(viewModelState = viewModelState, listener = uiState.listener)
                    }
                }
            }
        }.asStateFlow()

    private fun post() {
        val state = viewModelStateFlow.value
        if (state.postable().not()) return

        viewModelStateFlow.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val error = when (val result = api.postNote(state.body)) {
                UserPostNoteResult.Success -> null

                is UserPostNoteResult.Rejected -> when {
                    result.isEmpty -> "本文が空"
                    result.maxLength != null -> "${result.maxLength} 文字までにする"
                    else -> "投稿できなかった"
                }

                is UserPostNoteResult.Failure -> result.message
            }
            viewModelStateFlow.update { it.copy(submitting = false, error = error) }
            if (error == null) {
                events.send { it.navigateBack() }
            }
        }
    }

    private fun createUiState(
        viewModelState: ViewModelState,
        listener: PostScreenUiState.Listener,
    ): PostScreenUiState {
        val remainingLength = viewModelState.remainingLength()
        return PostScreenUiState(
            body = viewModelState.body,
            remainingLength = remainingLength,
            remainingLengthOver = remainingLength < 0,
            error = viewModelState.error,
            inputEnabled = viewModelState.submitting.not(),
            postButtonEnabled = viewModelState.postable(),
            listener = listener,
        )
    }

    private fun ViewModelState.postable(): Boolean = submitting.not() && body.isNotBlank() && remainingLength() >= 0

    /**
     * サーバーと同じく、書いた人にとっての文字数に合わせてコードポイントで数える
     */
    private fun ViewModelState.remainingLength(): Int = NOTE_MAX_LENGTH - body.trim().count { it.isLowSurrogate().not() }

    data class ViewModelState(
        val body: String,
        val submitting: Boolean = false,
        val error: String? = null,
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
         * 画面回転などで作り直されても書きかけの本文を残す
         */
        fun saver(
            viewModelScope: CoroutineScope,
            api: UserApi,
        ): Saver<PostScreenViewModel, String> = Saver(
            save = { viewModel -> viewModel.viewModelStateFlow.value.body },
            restore = { body ->
                PostScreenViewModel(
                    viewModelScope = viewModelScope,
                    api = api,
                    savedState = ViewModelState(body = body),
                )
            },
        )
    }
}
