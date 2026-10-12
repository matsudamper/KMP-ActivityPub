package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.runtime.Immutable

/**
 * Mastodon と同じく、カラムを横に並べる画面。いまは左端の投稿カラムだけ
 */
data class HomeScreenUiState(
    val composeColumn: ComposeColumn,
    val listener: Listener,
) {
    sealed interface ComposeColumn {
        data object Loading : ComposeColumn

        data class Error(
            val message: String,
        ) : ComposeColumn

        data class Login(
            val username: String,
            val password: String,
            val submitting: Boolean,
            val error: String?,
            val inputEnabled: Boolean,
            val loginButtonEnabled: Boolean,
        ) : ComposeColumn

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
