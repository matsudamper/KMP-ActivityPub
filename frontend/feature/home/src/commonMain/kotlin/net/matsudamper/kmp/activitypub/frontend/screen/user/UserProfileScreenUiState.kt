package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.compose.runtime.Immutable

data class UserProfileScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * @param iconUrl プロフィール画像が無ければ null
         */
        data class Loaded(
            val acct: String,
            val iconUrl: String?,
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
