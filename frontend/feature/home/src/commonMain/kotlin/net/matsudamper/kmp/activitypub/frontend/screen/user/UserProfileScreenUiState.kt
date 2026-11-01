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
            val displayName: String,
            val biography: String?,
            val followingText: String,
            val posts: List<Post>,
        ) : Content

        data object NotFound : Content

        data class Error(
            val message: String,
        ) : Content
    }

    data class Post(
        val content: String,
        val publishedAt: String,
    )

    @Immutable
    interface Listener {
        fun onClickRetry()

        fun onClickBack()
    }
}
