package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileLinkUiState

data class UserProfileScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * @param iconUrl プロフィール画像が無ければ null
         * @param followButton フォローできない相手には出さないので null
         */
        data class Loaded(
            val acct: String,
            val iconUrl: String?,
            val displayName: String,
            val biography: String?,
            val links: List<ProfileLinkUiState>,
            val followButton: FollowButton?,
            val posts: List<PostItemUiState>,
            val postsEmptyMessage: String?,
        ) : Content

        data object NotFound : Content

        data class Error(
            val message: String,
        ) : Content
    }

    data class FollowButton(
        val text: String,
        val enabled: Boolean,
    )

    @Immutable
    interface Listener {
        fun onClickRetry()

        fun onClickBack()

        fun onClickFollow()
    }
}
