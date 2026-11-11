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
     * このサーバーのユーザーには元のインスタンスが無いので false
     */
    val openOriginalMenuItemVisible: Boolean,
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
            val media: List<ProfileMediaUiState>,
            val mediaEmptyMessage: String?,
        ) : Content

        data object NotFound : Content

        data class Error(
            val message: String,
        ) : Content
    }

    @Immutable
    interface Listener {
        fun onClickRetry()

        fun onClickBack()

        fun onClickTab(tab: ProfileContentTab)

        fun onClickOpenOriginal()
    }
}
