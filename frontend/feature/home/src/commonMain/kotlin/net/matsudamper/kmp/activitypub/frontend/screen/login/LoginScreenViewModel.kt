package net.matsudamper.kmp.activitypub.frontend.screen.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLoginResult

internal class LoginScreenViewModel(
    savedStateHandle: SavedStateHandle,
    apiFlow: Flow<UserApi?>,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()

    private val usernameFlow: MutableStateFlow<String> = savedStateHandle.getMutableStateFlow(KEY_USERNAME, "")

    /**
     * 平文をプロセスの外に残さないため、SavedStateHandle には入れない
     */
    private val passwordFlow: MutableStateFlow<String> = MutableStateFlow("")
    private val apiStateFlow: StateFlow<UserApi?> = apiFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(
        ViewModelState(
            submitting = false,
            error = null,
        ),
    )

    private val listener = object : LoginScreenUiState.Listener {
        override fun onUsernameChanged(text: String) {
            usernameFlow.value = text
            clearError()
        }

        override fun onPasswordChanged(text: String) {
            passwordFlow.value = text
            clearError()
        }

        override fun onClickLogin() {
            login()
        }
    }

    val uiStateFlow: StateFlow<LoginScreenUiState> =
        combine(
            usernameFlow,
            passwordFlow,
            apiStateFlow,
            viewModelStateFlow,
        ) { username, password, api, viewModelState ->
            createUiState(
                username = username,
                password = password,
                loginable = loginable(
                    username = username,
                    password = password,
                    api = api,
                    viewModelState = viewModelState,
                ),
                viewModelState = viewModelState,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = createUiState(
                username = usernameFlow.value,
                password = passwordFlow.value,
                loginable = false,
                viewModelState = viewModelStateFlow.value,
            ),
        )

    private fun clearError() {
        viewModelStateFlow.update {
            it.copy(
                error = null,
            )
        }
    }

    private fun login() {
        val username = usernameFlow.value
        val password = passwordFlow.value
        val api = apiStateFlow.value ?: return
        val loginable = loginable(
            username = username,
            password = password,
            api = api,
            viewModelState = viewModelStateFlow.value,
        )
        if (loginable.not()) return

        viewModelStateFlow.update {
            it.copy(
                submitting = true,
                error = null,
            )
        }
        viewModelScope.launch {
            val error = try {
                when (val result = api.login(username = username.trim(), password = password)) {
                    UserLoginResult.Success -> null
                    UserLoginResult.WrongUsernameOrPassword -> "ユーザー名かパスワードが違う"
                    is UserLoginResult.Failure -> result.message
                }
            } finally {
                viewModelStateFlow.update {
                    it.copy(
                        submitting = false,
                    )
                }
            }
            viewModelStateFlow.update {
                it.copy(
                    error = error,
                )
            }
            if (error == null) {
                events.send { it.navigateToHome() }
            }
        }
    }

    private fun loginable(
        username: String,
        password: String,
        api: UserApi?,
        viewModelState: ViewModelState,
    ): Boolean = api != null && viewModelState.submitting.not() && username.isNotBlank() && password.isNotEmpty()

    private fun createUiState(
        username: String,
        password: String,
        loginable: Boolean,
        viewModelState: ViewModelState,
    ): LoginScreenUiState {
        return LoginScreenUiState(
            username = username,
            password = password,
            error = viewModelState.error,
            inputHasError = viewModelState.error != null,
            inputEnabled = viewModelState.submitting.not(),
            loginButtonEnabled = loginable,
            listener = listener,
        )
    }

    private data class ViewModelState(
        val submitting: Boolean,
        val error: String?,
    )

    interface Event {
        fun navigateToHome()
    }

    private companion object {
        const val KEY_USERNAME = "username"
    }
}
