package net.matsudamper.kmp.activitypub.frontend.screen.followlist

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.ui.AddFollowingGroupDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.FollowMenuUiState

data class FollowListScreenUiState(
    val title: String,
    val content: Content,
    /**
     * 出していなければ null
     */
    val addFollowingGroupDialog: AddFollowingGroupDialogUiState?,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data class Error(
            val message: String,
        ) : Content

        /**
         * @param emptyMessage 1 人もいないときに出す文。いれば null
         */
        data class Loaded(
            val accounts: List<Account>,
            val emptyMessage: String?,
            val footer: Footer,
        ) : Content
    }

    /**
     * 一覧の末尾。続きの読み込みの状態を出す
     */
    sealed interface Footer {
        data object None : Footer

        data object Loading : Footer

        data class Error(
            val message: String,
        ) : Footer
    }

    data class Account(
        val iconUrl: String?,
        val displayName: String,
        val acct: String,
        val followMenu: FollowMenuUiState,
        val clickable: Boolean,
        val listener: AccountListener,
    )

    @Immutable
    interface AccountListener {
        fun onClick()
    }

    @Immutable
    interface Listener {
        fun onClickBack()

        fun onClickRetry()

        fun onScrolledToEnd()

        fun onClickRetryLoadMore()
    }
}
