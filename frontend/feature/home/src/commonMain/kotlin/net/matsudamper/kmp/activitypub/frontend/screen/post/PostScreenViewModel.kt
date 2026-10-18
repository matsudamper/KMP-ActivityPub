package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNoteResult
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class PostScreenViewModel(
    private val api: UserApi,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(ViewModelState(body = savedStateHandle.get<String>(BODY_KEY).orEmpty()))

    val uiStateFlow: StateFlow<PostScreenUiState> =
        MutableStateFlow(
            createUiState(
                viewModelState = viewModelStateFlow.value,
                listener = object : PostScreenUiState.Listener {
                    override fun onBodyChanged(text: String) {
                        savedStateHandle[BODY_KEY] = text
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
            if (error == null) {
                savedStateHandle[BODY_KEY] = ""
                viewModelStateFlow.update { it.copy(body = "", submitting = false, error = null) }
                events.send { it.navigateBack() }
            } else {
                viewModelStateFlow.update { it.copy(submitting = false, error = error) }
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

    private companion object {
        /**
         * Mastodon の既定の投稿長。超えるとサーバーが弾く
         */
        const val NOTE_MAX_LENGTH = 500
    }
}

private const val BODY_KEY = "body"
