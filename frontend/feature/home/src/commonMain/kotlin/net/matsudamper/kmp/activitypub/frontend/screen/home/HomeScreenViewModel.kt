package net.matsudamper.kmp.activitypub.frontend.screen.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLoginResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNoteResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserSessionResult

internal class HomeScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: UserApi,
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())
    private var sessionJob: Job? = null

    val uiStateFlow: StateFlow<HomeScreenUiState> =
        MutableStateFlow(
            HomeScreenUiState(
                composeColumn = HomeScreenUiState.ComposeColumn.Loading,
                listener = object : HomeScreenUiState.Listener {
                    override fun onClickRetry() {
                        reload()
                    }

                    override fun onUsernameChanged(text: String) {
                        viewModelStateFlow.update { it.copy(username = text, error = null) }
                    }

                    override fun onPasswordChanged(text: String) {
                        viewModelStateFlow.update { it.copy(password = text, error = null) }
                    }

                    override fun onClickLogin() {
                        login()
                    }

                    override fun onBodyChanged(text: String) {
                        viewModelStateFlow.update { it.copy(body = text, error = null) }
                    }

                    override fun onClickPost() {
                        post()
                    }

                    override fun onClickLogout() {
                        logout()
                    }
                },
            ),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.update { uiState ->
                        uiState.copy(composeColumn = createComposeColumn(viewModelState))
                    }
                }
            }
        }.asStateFlow()

    fun onStart() {
        reload()
    }

    private fun reload() {
        sessionJob?.cancel()
        viewModelStateFlow.update { it.copy(session = null) }
        sessionJob = viewModelScope.launch {
            api.session().collect { session ->
                viewModelStateFlow.update { it.copy(session = session) }
            }
        }
    }

    private fun login() {
        val state = viewModelStateFlow.value
        if (state.submitting || state.username.isBlank() || state.password.isEmpty()) return

        viewModelStateFlow.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val error = when (val result = api.login(username = state.username.trim(), password = state.password)) {
                UserLoginResult.Success -> null
                UserLoginResult.WrongUsernameOrPassword -> "ユーザー名かパスワードが違う"
                is UserLoginResult.Failure -> result.message
            }
            viewModelStateFlow.update {
                it.copy(
                    submitting = false,
                    error = error,
                    password = if (error == null) "" else it.password,
                )
            }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            api.logout()
            viewModelStateFlow.update { it.copy(body = "", error = null) }
        }
    }

    private fun post() {
        val state = viewModelStateFlow.value
        if (state.submitting || state.body.isBlank()) return

        viewModelStateFlow.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = api.postNote(state.body)) {
                UserPostNoteResult.Success -> {
                    viewModelStateFlow.update { it.copy(submitting = false, body = "") }
                    events.send { it.showSnackbar("投稿した") }
                }

                is UserPostNoteResult.Rejected -> {
                    val message = when {
                        result.isEmpty -> "本文が空"
                        result.maxLength != null -> "${result.maxLength} 文字までにする"
                        else -> "投稿できなかった"
                    }
                    viewModelStateFlow.update { it.copy(submitting = false, error = message) }
                }

                is UserPostNoteResult.Failure -> {
                    viewModelStateFlow.update { it.copy(submitting = false, error = result.message) }
                }
            }
        }
    }

    private fun createComposeColumn(state: ViewModelState): HomeScreenUiState.ComposeColumn {
        return when (val session = state.session) {
            null -> HomeScreenUiState.ComposeColumn.Loading

            is UserSessionResult.Failure -> HomeScreenUiState.ComposeColumn.Error(session.message)

            UserSessionResult.LoggedOut -> HomeScreenUiState.ComposeColumn.Login(
                username = state.username,
                password = state.password,
                submitting = state.submitting,
                error = state.error,
                inputEnabled = !state.submitting,
                loginButtonEnabled = !state.submitting && state.username.isNotBlank() && state.password.isNotEmpty(),
            )

            is UserSessionResult.LoggedIn -> {
                // サーバーと同じく、書いた人にとっての文字数に合わせてコードポイントで数える
                val remaining = NOTE_MAX_LENGTH - state.body.trim().codePointCount()
                HomeScreenUiState.ComposeColumn.Compose(
                    acct = session.acct,
                    body = state.body,
                    remainingLength = remaining,
                    remainingLengthOver = remaining < 0,
                    submitting = state.submitting,
                    error = state.error,
                    bodyInputEnabled = !state.submitting,
                    postButtonEnabled = !state.submitting && state.body.isNotBlank() && remaining >= 0,
                )
            }
        }
    }

    private fun String.codePointCount(): Int = count { !it.isLowSurrogate() }

    private data class ViewModelState(
        val session: UserSessionResult? = null,
        val username: String = "",
        val password: String = "",
        val body: String = "",
        val submitting: Boolean = false,
        val error: String? = null,
    )

    interface Event {
        fun showSnackbar(message: String)
    }

    private companion object {
        /**
         * Mastodon の既定の投稿長。超えるとサーバーが弾く
         */
        const val NOTE_MAX_LENGTH = 500
    }
}
