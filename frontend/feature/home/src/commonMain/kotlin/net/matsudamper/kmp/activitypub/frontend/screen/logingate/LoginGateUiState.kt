package net.matsudamper.kmp.activitypub.frontend.screen.logingate

import androidx.compose.runtime.Immutable

data class LoginGateUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data object LoginRequired : Content

        data object Screen : Content

        data class Error(
            val message: String,
        ) : Content
    }

    @Immutable
    interface Listener {
        fun onLoggedIn()

        fun onClickRetry()

        fun onLoginRequiredScreenShown()
    }
}
