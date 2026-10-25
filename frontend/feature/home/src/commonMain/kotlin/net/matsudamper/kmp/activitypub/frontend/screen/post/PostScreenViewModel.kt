package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNoteResult
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class PostScreenViewModel(
    private val postNoteSubmitter: PostNoteSubmitter,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                body = savedStateHandle.get<String>(BODY_KEY).orEmpty(),
                submitting = false,
                error = null,
            ),
        )

    val uiStateFlow: StateFlow<PostScreenUiState> =
        MutableStateFlow(
            createUiState(
                viewModelState = viewModelStateFlow.value,
                listener = object : PostScreenUiState.Listener {
                    override fun onBodyChanged(text: String) {
                        savedStateHandle[BODY_KEY] = text
                        // 応答が失われて送り直すときだけ同じ ID にし、書き直した本文は別の投稿として扱う
                        savedStateHandle[REQUEST_ID_KEY] = newRequestId()
                        viewModelStateFlow.update { it.copy(body = text, error = null) }
                    }

                    override fun onClickPost() {
                        post()
                    }

                    override fun onClickBack() {
                        // 送信中に閉じると、投稿できたかどうかを画面に出せない
                        if (viewModelStateFlow.value.submitting) return
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
                        createUiState(
                            viewModelState = viewModelState,
                            listener = uiState.listener,
                        )
                    }
                }
            }
        }.asStateFlow()

    private fun post() {
        val state = viewModelStateFlow.value
        if (state.postable().not()) return

        viewModelStateFlow.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val error =
                try {
                    when (val result = postNoteSubmitter.submit(body = state.body, requestId = requestId())) {
                        UserPostNoteResult.Success -> null
                        UserPostNoteResult.NotLoggedIn -> "ログインしていません"
                        UserPostNoteResult.EmptyBody -> "本文が空です"
                        UserPostNoteResult.TooLong -> "本文が長すぎます"
                        UserPostNoteResult.FederationDisabled -> "サーバーが投稿を配れる設定になっていません"
                        is UserPostNoteResult.Failure -> result.message
                    }
                } finally {
                    viewModelStateFlow.update { it.copy(submitting = false) }
                }
            if (error != null) {
                viewModelStateFlow.update { it.copy(error = error) }
                events.send { it.showMessage(error) }
                return@launch
            }
            savedStateHandle[BODY_KEY] = ""
            savedStateHandle[REQUEST_ID_KEY] = newRequestId()
            viewModelStateFlow.update { it.copy(body = "") }
            events.send { it.navigateBack() }
        }
    }

    private fun requestId(): String =
        savedStateHandle.get<String>(REQUEST_ID_KEY)
            ?: newRequestId().also { savedStateHandle[REQUEST_ID_KEY] = it }

    private fun newRequestId(): String = Uuid.random().toString()

    private fun createUiState(
        viewModelState: ViewModelState,
        listener: PostScreenUiState.Listener,
    ): PostScreenUiState =
        PostScreenUiState(
            body = viewModelState.body,
            inputHasError = viewModelState.error != null,
            inputEnabled = viewModelState.submitting.not(),
            backButtonEnabled = viewModelState.submitting.not(),
            postButtonEnabled = viewModelState.postable(),
            listener = listener,
        )

    private fun ViewModelState.postable(): Boolean = submitting.not() && body.isNotBlank()

    data class ViewModelState(
        val body: String,
        val submitting: Boolean,
        val error: String?,
    )

    interface Event {
        fun navigateBack()

        fun showMessage(message: String)
    }
}

private const val BODY_KEY = "body"
private const val REQUEST_ID_KEY = "requestId"
