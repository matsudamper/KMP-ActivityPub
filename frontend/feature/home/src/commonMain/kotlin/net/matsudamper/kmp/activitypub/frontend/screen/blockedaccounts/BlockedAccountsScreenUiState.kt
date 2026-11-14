package net.matsudamper.kmp.activitypub.frontend.screen.blockedaccounts

import androidx.compose.runtime.Immutable

data class BlockedAccountsScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * @param accounts 新しくブロックした順
         */
        data class Loaded(
            val accounts: List<Account>,
            val emptyMessageVisible: Boolean,
        ) : Content

        /**
         * 読めなかった理由は Snackbar で出すので、ここでは読み直す操作だけを出す
         */
        data object LoadFailed : Content
    }

    /**
     * @param acct `@username@host` の形
     */
    data class Account(
        val acct: String,
        val unblockButtonEnabled: Boolean,
        val listener: AccountListener,
    )

    @Immutable
    interface AccountListener {
        fun onClickUnblock()
    }

    @Immutable
    interface Listener {
        fun onClickBack()

        fun onClickRetry()
    }
}
