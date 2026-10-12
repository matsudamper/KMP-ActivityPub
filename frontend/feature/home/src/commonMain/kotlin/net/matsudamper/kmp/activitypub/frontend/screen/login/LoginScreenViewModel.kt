package net.matsudamper.kmp.activitypub.frontend.screen.login

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender

/**
 * サーバーとはまだ繋いでいない。入力があればログインできたことにする
 */
internal class LoginScreenViewModel(
    private val viewModelScope: CoroutineScope,
    savedState: ViewModelState,
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(savedState)

    val uiStateFlow: StateFlow<LoginScreenUiState> =
        MutableStateFlow(
            LoginScreenUiState(
                username = savedState.username,
                password = savedState.password,
                loginButtonEnabled = savedState.inputFilled(),
                listener = object : LoginScreenUiState.Listener {
                    override fun onUsernameChanged(text: String) {
                        viewModelStateFlow.update { it.copy(username = text) }
                    }

                    override fun onPasswordChanged(text: String) {
                        viewModelStateFlow.update { it.copy(password = text) }
                    }

                    override fun onClickLogin() {
                        login()
                    }
                },
            ),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.update { uiState ->
                        uiState.copy(
                            username = viewModelState.username,
                            password = viewModelState.password,
                            loginButtonEnabled = viewModelState.inputFilled(),
                        )
                    }
                }
            }
        }.asStateFlow()

    private fun login() {
        if (viewModelStateFlow.value.inputFilled().not()) return

        viewModelScope.launch {
            events.send { it.navigateToHome() }
        }
    }

    private fun ViewModelState.inputFilled(): Boolean = username.isNotBlank() && password.isNotEmpty()

    data class ViewModelState(
        val username: String,
        val password: String,
    )

    interface Event {
        fun navigateToHome()
    }

    companion object {
        val InitialState = ViewModelState(username = "", password = "")

        /**
         * 画面回転などで作り直されても入力を残す
         */
        fun saver(viewModelScope: CoroutineScope): Saver<LoginScreenViewModel, Any> = listSaver(
            save = { viewModel ->
                val state = viewModel.viewModelStateFlow.value
                listOf(state.username, state.password)
            },
            restore = { (username, password) ->
                LoginScreenViewModel(
                    viewModelScope = viewModelScope,
                    savedState = ViewModelState(username = username, password = password),
                )
            },
        )
    }
}
