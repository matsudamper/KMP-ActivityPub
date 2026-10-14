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
import org.koin.core.annotation.KoinViewModel

/**
 * サーバーとはまだ繋いでいない。投稿は常に成功したことにする
 */
@KoinViewModel
internal class PostScreenViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(ViewModelState(body = savedStateHandle.get<String>(BODY_KEY).orEmpty()))

    val uiStateFlow: StateFlow<PostScreenUiState> =
        MutableStateFlow(
            PostScreenUiState(
                body = viewModelStateFlow.value.body,
                postButtonEnabled = viewModelStateFlow.value.postable(),
                listener = object : PostScreenUiState.Listener {
                    override fun onBodyChanged(text: String) {
                        savedStateHandle[BODY_KEY] = text
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
}

private const val BODY_KEY = "body"
