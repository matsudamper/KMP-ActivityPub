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
                postButtonEnabled = savedState.postable(),
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
                    uiStateFlow.update { uiState ->
                        uiState.copy(
                            body = viewModelState.body,
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

    private fun ViewModelState.postable(): Boolean = body.isNotBlank()

    data class ViewModelState(
        val body: String,
    )

    interface Event {
        fun navigateBack()
    }

    companion object {
        val InitialState = ViewModelState(body = "")

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
