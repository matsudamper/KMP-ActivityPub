package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

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
            val selectedTab: Tab,
            val posts: List<PostItemUiState>,
            val postsEmptyMessage: String?,
            val media: List<Media>,
            val mediaEmptyMessage: String?,
        ) : Content

        data object NotFound : Content

        data class Error(
            val message: String,
        ) : Content
    }

    enum class Tab {
        Posts,
        Media,
    }

    /**
     * @param description 画像の説明文。無ければ null
     * @param listener 個別投稿画面で開けない投稿の画像は押せないので null
     */
    data class Media(
        val url: String,
        val description: String?,
        val blurred: Boolean,
        val listener: MediaListener?,
    )

    @Immutable
    interface MediaListener {
        fun onClick()
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

        fun onClickTab(tab: Tab)
    }
}
