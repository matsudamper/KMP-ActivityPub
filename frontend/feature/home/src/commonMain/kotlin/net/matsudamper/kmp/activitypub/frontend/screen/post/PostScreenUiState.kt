package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.runtime.Immutable

data class PostScreenUiState(
    val body: String,
    val inputHasError: Boolean,
    val inputEnabled: Boolean,
    /**
     * 閉じると送信の結果を画面に出せないので、送信の途中は false になる
     */
    val backButtonEnabled: Boolean,
    val postButtonEnabled: Boolean,
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onBodyChanged(text: String)

        fun onClickPost()

        fun onClickBack()
    }
}
