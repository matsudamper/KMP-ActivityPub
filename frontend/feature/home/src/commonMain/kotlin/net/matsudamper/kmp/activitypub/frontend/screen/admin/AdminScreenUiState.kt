package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.compose.runtime.Immutable

data class AdminScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data object Empty : Content

        data class Message(
            val message: String,
        ) : Content
    }

    @Immutable
    interface Listener {
        fun onClickBack()
    }
}
