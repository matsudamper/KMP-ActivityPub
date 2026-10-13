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
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLoginResult

internal class LoginScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: UserApi?,
    savedState: ViewModelState,
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(savedState)

    val uiStateFlow: StateFlow<LoginScreenUiState> =
        MutableStateFlow(
            createUiState(
                viewModelState = savedState,
                listener = object : LoginScreenUiState.Listener {
                    override fun onUsernameChanged(text: String) {
                        viewModelStateFlow.update {
                            it.copy(
                                username = text,
                                error = null,
                            )
                        }
                    }

                    override fun onPasswordChanged(text: String) {
                        viewModelStateFlow.update {
                            it.copy(
                                password = text,
                                error = null,
                            )
                        }
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
                        createUiState(
                            viewModelState = viewModelState,
                            listener = uiState.listener,
                        )
                    }
                }
            }
        }.asStateFlow()

    private fun login() {
        val state = viewModelStateFlow.value
        val api = api ?: return
        if (state.loginable().not()) return

        viewModelStateFlow.update {
            it.copy(
                submitting = true,
                error = null,
            )
        }
        viewModelScope.launch {
            val error = try {
                when (val result = api.login(username = state.username.trim(), password = state.password)) {
                    UserLoginResult.Success -> null
                    UserLoginResult.WrongUsernameOrPassword -> "ユーザー名かパスワードが違う"
                    is UserLoginResult.Failure -> result.message
                }
            } finally {
                viewModelStateFlow.update { it.copy(submitting = false) }
            }
            viewModelStateFlow.update { it.copy(error = error) }
            if (error == null) {
                events.send { it.navigateToHome() }
            }
        }
    }

    private fun createUiState(
        viewModelState: ViewModelState,
        listener: LoginScreenUiState.Listener,
    ): LoginScreenUiState {
        return LoginScreenUiState(
            username = viewModelState.username,
            password = viewModelState.password,
            error = viewModelState.error,
            inputHasError = viewModelState.error != null,
            inputEnabled = viewModelState.submitting.not(),
            loginButtonEnabled = viewModelState.loginable(),
            listener = listener,
        )
    }

    private fun ViewModelState.loginable(): Boolean =
        api != null && submitting.not() && username.isNotBlank() && password.isNotEmpty()

    data class ViewModelState(
        val username: String,
        val password: String,
        val submitting: Boolean = false,
        val error: String? = null,
    )

    interface Event {
        fun navigateToHome()
    }

    companion object {
        val InitialState = ViewModelState(
            username = "",
            password = "",
        )

        fun saver(
            viewModelScope: CoroutineScope,
            api: UserApi?,
        ): Saver<LoginScreenViewModel, Any> = listSaver(
            save = { viewModel ->
                val state = viewModel.viewModelStateFlow.value
                listOf(state.username, state.password)
            },
            restore = { (username, password) ->
                LoginScreenViewModel(
                    viewModelScope = viewModelScope,
                    api = api,
                    savedState = ViewModelState(username = username, password = password),
                )
            },
        )
    }
}
