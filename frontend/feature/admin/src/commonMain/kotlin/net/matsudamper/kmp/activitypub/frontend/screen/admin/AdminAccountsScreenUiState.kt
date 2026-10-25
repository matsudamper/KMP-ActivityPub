package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.ui.AdminScaffoldListener

data class AdminAccountsScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data object RequireLogin : Content

        /**
         * @param loadMoreOnVisible 枠が見えたら続きを取りに行く。取っている間と失敗した後は false
         */
        data class Loaded(
            val accounts: List<Account>,
            val loadMoreVisible: Boolean,
            val loadMoreOnVisible: Boolean,
            val loadMoreErrorMessage: String?,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    data class Account(
        val username: String,
        val acct: String?,
    )

    @Immutable
    interface Listener : AdminScaffoldListener {
        fun onClickNewAccount()

        fun onClickReload()

        fun onLoadMore()
    }
}
