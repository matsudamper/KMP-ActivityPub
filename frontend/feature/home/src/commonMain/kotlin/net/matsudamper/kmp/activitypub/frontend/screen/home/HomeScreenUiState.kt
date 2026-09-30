package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.runtime.Immutable

/**
 * Mastodon と同じく、カラムを横に並べる画面。いまは左端の投稿カラムだけ
 */
data class HomeScreenUiState(
    val composeColumn: ComposeColumn,
    val listener: Listener,
) {
    /**
     * 左端のカラム。ログインしていなければログインの入力を、していれば投稿の入力を出す
     */
    sealed interface ComposeColumn {
        data object Loading : ComposeColumn

        data class Error(
            val message: String,
        ) : ComposeColumn

        /**
         * @param submitting true の間はボタンの文字を待ち状態にする
         * @param error 入力欄の下に赤字で出す。null なら何も出さない
         */
        data class Login(
            val username: String,
            val password: String,
            val submitting: Boolean,
            val error: String?,
            val inputEnabled: Boolean,
            val loginButtonEnabled: Boolean,
        ) : ComposeColumn

        /**
         * @param acct カラムの上に出す、ログイン中のユーザー
         * @param remainingLength 入力欄の下に出す残りの文字数
         * @param remainingLengthOver true なら残りの文字数を赤字で出す
         * @param submitting true の間はボタンの文字を待ち状態にする
         * @param error 入力欄の下に赤字で出す。null なら何も出さない
         */
        data class Compose(
            val acct: String,
            val body: String,
            val remainingLength: Int,
            val remainingLengthOver: Boolean,
            val submitting: Boolean,
            val error: String?,
            val bodyInputEnabled: Boolean,
            val postButtonEnabled: Boolean,
        ) : ComposeColumn
    }

    @Immutable
    interface Listener {
        fun onClickRetry()

        fun onUsernameChanged(text: String)

        fun onPasswordChanged(text: String)

        fun onClickLogin()

        fun onBodyChanged(text: String)

        fun onClickPost()

        fun onClickLogout()
    }
}
