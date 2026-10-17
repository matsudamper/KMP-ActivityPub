package net.matsudamper.kmp.activitypub.frontend.screen.logingate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLoginStateResult
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class LoginGateViewModel(
    private val api: UserApi,
) : ViewModel() {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState.Checking)
    private var checkLoginStateJob: Job? = null

    val uiStateFlow: StateFlow<LoginGateUiState> =
        MutableStateFlow(
            createUiState(
                viewModelState = viewModelStateFlow.value,
                listener =
                object : LoginGateUiState.Listener {
                    override fun onLoggedIn() {
                        viewModelStateFlow.value = ViewModelState.LoggedIn
                    }

                    override fun onClickRetry() {
                        checkLoginState()
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
        checkLoginState()
    }

    private fun checkLoginState() {
        checkLoginStateJob?.cancel()
        viewModelStateFlow.value = ViewModelState.Checking
        checkLoginStateJob = viewModelScope.launch {
            viewModelStateFlow.value =
                when (val result = api.loginState()) {
                    UserLoginStateResult.LoggedIn -> ViewModelState.LoggedIn
                    UserLoginStateResult.NotLoggedIn -> ViewModelState.NotLoggedIn
                    is UserLoginStateResult.Failure -> ViewModelState.Failed(result.message)
                }
        }
    }

    private fun createUiState(
        viewModelState: ViewModelState,
        listener: LoginGateUiState.Listener,
    ): LoginGateUiState {
        return LoginGateUiState(
            content = when (viewModelState) {
                ViewModelState.Checking -> LoginGateUiState.Content.Loading
                ViewModelState.NotLoggedIn -> LoginGateUiState.Content.LoginRequired
                ViewModelState.LoggedIn -> LoginGateUiState.Content.Screen
                is ViewModelState.Failed -> LoginGateUiState.Content.Error(viewModelState.message)
            },
            listener = listener,
        )
    }

    sealed interface ViewModelState {
        data object Checking : ViewModelState

        data object NotLoggedIn : ViewModelState

        data object LoggedIn : ViewModelState

        data class Failed(
            val message: String,
        ) : ViewModelState
    }
}
