package net.matsudamper.kmp.activitypub.frontend.screen.login

import androidx.compose.runtime.saveable.Saver
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
                        viewModelStateFlow.update { it.copy(username = text.take(INPUT_MAX_LENGTH)) }
                    }

                    override fun onPasswordChanged(text: String) {
                        viewModelStateFlow.update { it.copy(password = text.take(INPUT_MAX_LENGTH)) }
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
         * ユーザー名は Activity の保存状態に入る。Binder の転送上限を超えるとクラッシュするので打ち切る
         */
        private const val INPUT_MAX_LENGTH = 256

        /**
         * 画面回転などで作り直されてもユーザー名を残す。
         * 保存状態はプロセスの復元用にディスクへ書かれうるので、パスワードは入れない
         */
        fun saver(viewModelScope: CoroutineScope): Saver<LoginScreenViewModel, String> = Saver(
            save = { viewModel -> viewModel.viewModelStateFlow.value.username },
            restore = { username ->
                LoginScreenViewModel(
                    viewModelScope = viewModelScope,
                    savedState = ViewModelState(username = username, password = ""),
                )
            },
        )
    }
}
