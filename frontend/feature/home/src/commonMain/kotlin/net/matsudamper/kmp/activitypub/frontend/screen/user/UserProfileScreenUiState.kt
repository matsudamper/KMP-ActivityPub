package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.compose.runtime.Immutable

data class UserProfileScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data class Loaded(
            val acct: String,
        ) : Content

        data object NotFound : Content

        data class Error(
            val message: String,
        ) : Content
    }

    @Immutable
    interface Listener {
        fun onClickRetry()
    }
}
