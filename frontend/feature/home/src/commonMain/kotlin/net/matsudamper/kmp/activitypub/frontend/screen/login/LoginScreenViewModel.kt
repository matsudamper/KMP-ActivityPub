package net.matsudamper.kmp.activitypub.frontend.screen.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyApi
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAuthenticator
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyLoginResult
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyOptionsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLoginResult
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class LoginScreenViewModel(
    private val api: UserApi,
    private val passkeyApi: PasskeyApi,
    private val passkeyAuthenticator: PasskeyAuthenticator,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                username = savedStateHandle.get<String>(USERNAME_KEY).orEmpty(),
                password = "",
                submitting = false,
                inputHasError = false,
                apiReady = api.isReady(),
            ),
        )

    val uiStateFlow: StateFlow<LoginScreenUiState> =
        MutableStateFlow(
            createUiState(
                viewModelState = viewModelStateFlow.value,
                listener =
                object : LoginScreenUiState.Listener {
                    override fun onUsernameChanged(text: String) {
                        savedStateHandle[USERNAME_KEY] = text
                        viewModelStateFlow.update {
                            it.copy(
                                username = text,
                                inputHasError = false,
                            )
                        }
                    }

                    override fun onPasswordChanged(text: String) {
                        viewModelStateFlow.update {
                            it.copy(
                                password = text,
                                inputHasError = false,
                            )
                        }
                    }

                    override fun onClickLogin() {
                        login()
                    }

                    override fun onClickPasskeyLogin() {
                        loginWithPasskey()
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

    init {
        viewModelScope.launch {
            api.ready.collect { ready ->
                viewModelStateFlow.update { it.copy(apiReady = ready) }
            }
        }
    }

    private fun login() {
        val state = viewModelStateFlow.value
        if (state.loginable().not()) return

        viewModelStateFlow.update {
            it.copy(
                submitting = true,
                inputHasError = false,
            )
        }
        viewModelScope.launch {
            val error =
                try {
                    when (val result = api.login(username = state.username.trim(), password = state.password)) {
                        UserLoginResult.Success -> null
                        UserLoginResult.WrongUsernameOrPassword -> "ユーザー名かパスワードが違う"
                        is UserLoginResult.Failure -> result.message
                    }
                } finally {
                    viewModelStateFlow.update { it.copy(submitting = false) }
                }
            if (error == null) {
                events.send { it.loggedIn() }
            } else {
                showError(error)
            }
        }
    }

    private fun loginWithPasskey() {
        if (viewModelStateFlow.value.passkeyLoginable().not()) return

        viewModelStateFlow.update {
            it.copy(
                submitting = true,
                inputHasError = false,
            )
        }
        viewModelScope.launch {
            val result =
                try {
                    authenticateWithPasskey()
                } finally {
                    viewModelStateFlow.update { it.copy(submitting = false) }
                }
            when (result) {
                PasskeyLoginOutcome.Success -> events.send { it.loggedIn() }
                PasskeyLoginOutcome.Cancelled -> Unit
                is PasskeyLoginOutcome.Error -> showError(result.message)
            }
        }
    }

    private suspend fun showError(message: String) {
        viewModelStateFlow.update { it.copy(inputHasError = true) }
        events.send { it.showMessage(message) }
    }

    private suspend fun authenticateWithPasskey(): PasskeyLoginOutcome {
        val request =
            when (val options = passkeyApi.loginOptions()) {
                is PasskeyOptionsResult.Success -> options.options
                PasskeyOptionsResult.NotLoggedIn -> return PasskeyLoginOutcome.Error("パスキーでログインできない")
                is PasskeyOptionsResult.Failure -> return PasskeyLoginOutcome.Error(options.message)
            }
        val assertion =
            when (val result = passkeyAuthenticator.get(request)) {
                is PasskeyAuthenticator.Result.Success -> result.value
                PasskeyAuthenticator.Result.Cancelled -> return PasskeyLoginOutcome.Cancelled
                is PasskeyAuthenticator.Result.Failure -> return PasskeyLoginOutcome.Error(result.message)
            }
        return when (val result = passkeyApi.login(assertion)) {
            PasskeyLoginResult.Success -> PasskeyLoginOutcome.Success
            PasskeyLoginResult.NotAccepted -> PasskeyLoginOutcome.Error("このパスキーではログインできない")
            is PasskeyLoginResult.Failure -> PasskeyLoginOutcome.Error(result.message)
        }
    }

    private fun createUiState(
        viewModelState: ViewModelState,
        listener: LoginScreenUiState.Listener,
    ): LoginScreenUiState {
        return LoginScreenUiState(
            username = viewModelState.username,
            password = viewModelState.password,
            inputHasError = viewModelState.inputHasError,
            inputEnabled = viewModelState.submitting.not(),
            loginButtonEnabled = viewModelState.loginable(),
            passkeyLoginButtonVisible = passkeyAuthenticator.available,
            passkeyLoginButtonEnabled = viewModelState.passkeyLoginable(),
            listener = listener,
        )
    }

    private fun ViewModelState.loginable(): Boolean =
        apiReady && submitting.not() && username.isNotBlank() && password.isNotEmpty()

    private fun ViewModelState.passkeyLoginable(): Boolean = apiReady && submitting.not() && passkeyAuthenticator.available

    data class ViewModelState(
        val username: String,
        val password: String,
        val submitting: Boolean,
        val inputHasError: Boolean,
        val apiReady: Boolean,
    )

    private sealed interface PasskeyLoginOutcome {
        data object Success : PasskeyLoginOutcome

        data object Cancelled : PasskeyLoginOutcome

        data class Error(
            val message: String,
        ) : PasskeyLoginOutcome
    }

    interface Event {
        fun loggedIn()

        fun showMessage(message: String)
    }
}

private const val USERNAME_KEY = "username"
