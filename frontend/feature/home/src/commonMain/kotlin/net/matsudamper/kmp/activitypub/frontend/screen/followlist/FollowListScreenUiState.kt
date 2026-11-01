package net.matsudamper.kmp.activitypub.frontend.screen.followlist

import androidx.compose.runtime.Immutable

data class FollowListScreenUiState(
    val title: String,
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * 読めなかった理由はメッセージで出す
         */
        data object NotLoaded : Content

        /**
         * @param emptyMessage 1 人もいないときに出す文。いれば null
         */
        data class Loaded(
            val accounts: List<Account>,
            val emptyMessage: String?,
            val loadMoreButtonVisible: Boolean,
        ) : Content
    }

    data class Account(
        val iconUrl: String?,
        val displayName: String,
        val acct: String,
        val followButtonText: String,
        val followButtonEnabled: Boolean,
        val listener: AccountListener,
    )

    @Immutable
    interface AccountListener {
        fun onClickFollow()
    }

    @Immutable
    interface Listener {
        fun onClickBack()

        fun onClickRetry()

        fun onClickLoadMore()
    }
}
