package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaUiState

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
            val followButton: FollowButton?,
            val selectedTab: ProfileContentTab,
            val posts: List<PostItemUiState>,
            val postsEmptyMessage: String?,
            val media: List<ProfileMediaUiState>,
            val mediaEmptyMessage: String?,
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

        fun onClickTab(tab: ProfileContentTab)
    }
}
