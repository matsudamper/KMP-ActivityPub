package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.runtime.Immutable

data class PostScreenUiState(
    val body: String,
    val remainingLength: Int,
    val remainingLengthOver: Boolean,
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
