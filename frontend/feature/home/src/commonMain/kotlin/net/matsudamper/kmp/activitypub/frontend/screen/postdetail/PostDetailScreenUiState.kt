package net.matsudamper.kmp.activitypub.frontend.screen.postdetail

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

data class PostDetailScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data class Loaded(
            val post: PostItemUiState,
        ) : Content

        data object NotFound : Content

        data object Error : Content
    }

    @Immutable
    interface Listener {
        fun onClickRetry()

        fun onClickBack()
    }
}
