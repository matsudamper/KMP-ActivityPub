package net.matsudamper.kmp.activitypub.frontend.screen.login

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
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    val uiStateFlow: StateFlow<LoginScreenUiState> =
        MutableStateFlow(
            LoginScreenUiState(
                username = "",
                password = "",
                loginButtonEnabled = false,
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

    private data class ViewModelState(
        val username: String = "",
        val password: String = "",
    )

    interface Event {
        fun navigateToHome()
    }
}
