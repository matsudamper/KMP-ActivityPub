package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.ui.AdminScaffoldListener

data class AdminAccountNewScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data object RequireLogin : Content

        /**
         * @param submitting true の間は入力欄とボタンを押せなくし、ボタンの文字を待ち状態にする
         */
        data class Input(
            val username: String,
            val password: String,
            val submitting: Boolean,
            val error: String?,
            val inputEnabled: Boolean,
            val addButtonEnabled: Boolean,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    @Immutable
    interface Listener : AdminScaffoldListener {
        fun onUsernameChanged(text: String)

        fun onPasswordChanged(text: String)

        fun onClickAdd()
    }
}
