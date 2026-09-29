package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.ui.AdminScaffoldListener

data class AdminAccountsScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * ログインしていない。管理画面のトップに送る
         */
        data object RequireLogin : Content

        /**
         * @param loadMoreVisible 末尾に続きの枠を出す
         * @param loadMoreOnVisible 枠が見えたら続きを取りに行く。取っている間と失敗した後は false
         * @param loadMoreErrorMessage 続きが取れなかったときの文言。押して再試行するボタンと一緒に出す
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

    /**
     * 一覧の 1 行。
     *
     * @param username 行の見出し
     * @param acct 見出しの下に出す
     * @param createdAt 「登録: <値>」の形で出す
     */
    data class Account(
        val username: String,
        val acct: String,
        val createdAt: String,
    )

    @Immutable
    interface Listener : AdminScaffoldListener {
        fun onClickNewAccount()

        fun onClickReload()

        fun onLoadMore()
    }
}
