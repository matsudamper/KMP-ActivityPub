package net.matsudamper.kmp.activitypub.frontend.screen.login

import androidx.compose.runtime.Immutable

data class LoginScreenUiState(
    val username: String,
    val password: String,
    val error: String?,
    val inputHasError: Boolean,
    val inputEnabled: Boolean,
    val loginButtonEnabled: Boolean,
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onUsernameChanged(text: String)

        fun onPasswordChanged(text: String)

        fun onClickLogin()
    }
}
