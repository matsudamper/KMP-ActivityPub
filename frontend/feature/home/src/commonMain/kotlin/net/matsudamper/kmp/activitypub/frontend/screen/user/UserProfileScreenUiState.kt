package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.ui.AddFollowingGroupDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.FollowMenuUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileLinkUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaUiState
import net.matsudamper.kmp.activitypub.frontend.ui.TextWithEmojis

data class UserProfileScreenUiState(
    val content: Content,
    /**
     * 出していなければ null
     */
    val deleteNoteDialog: DeleteNoteDialogUiState?,
    /**
     * 出していなければ null
     */
    val addFollowingGroupDialog: AddFollowingGroupDialogUiState?,
    /**
     * 出していなければ null
     */
    val blockServerDialog: BlockServerDialog?,
    val openOriginalMenuItemVisible: Boolean,
    /**
     * このサーバーのユーザーには出さない
     */
    val blockServerMenuItemVisible: Boolean,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * @param iconUrl プロフィール画像が無ければ null
         * @param headerUrl ヘッダー画像が無ければ null
         * @param followMenu 購読できない相手には出さないので null
         */
        data class Loaded(
            val acct: String,
            val iconUrl: String?,
            val headerUrl: String?,
            val displayName: TextWithEmojis,
            val biography: String?,
            val links: List<ProfileLinkUiState>,
            val followMenu: FollowMenuUiState?,
            val selectedTab: ProfileContentTab,
            val posts: List<PostItemUiState>,
            val postsEmptyMessage: String?,
            val postsFooter: PostsFooter,
            val media: List<ProfileMediaUiState>,
            val mediaEmptyMessage: String?,
        ) : Content

        data object NotFound : Content

        data class Error(
            val message: String,
        ) : Content
    }

    /**
     * 投稿の一覧の末尾。続きの読み込みの状態を出す
     */
    sealed interface PostsFooter {
        data object None : PostsFooter

        data object Loading : PostsFooter

        data class Error(
            val message: String,
        ) : PostsFooter
    }

    /**
     * @param host ブロックするサーバー
     */
    data class BlockServerDialog(
        val host: String,
        val buttonsEnabled: Boolean,
        val listener: BlockServerDialogListener,
    )

    @Immutable
    interface BlockServerDialogListener {
        fun onClickBlock()

        fun onDismiss()
    }

    @Immutable
    interface Listener {
        fun onClickRetry()

        fun onClickBack()

        fun onClickTab(tab: ProfileContentTab)

        fun onClickOpenOriginal()

        fun onClickBlockServer()

        fun onScrolledToEnd()

        fun onClickRetryLoadMore()
    }
}
