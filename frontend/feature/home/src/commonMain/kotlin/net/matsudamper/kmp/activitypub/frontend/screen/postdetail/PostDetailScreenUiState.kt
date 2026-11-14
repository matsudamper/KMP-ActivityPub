package net.matsudamper.kmp.activitypub.frontend.screen.postdetail

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

data class PostDetailScreenUiState(
    val content: Content,
    /**
     * 出していなければ null
     */
    val deleteNoteDialog: DeleteNoteDialogUiState?,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data class Loaded(
            val post: PostItemUiState,
            val mentionBox: MentionBox,
        ) : Content

        data object NotFound : Content

        data object Error : Content
    }

    /**
     * 投稿の下に置く、投稿者へのメンションの入力欄
     */
    data class MentionBox(
        val text: String,
        val placeholder: String,
        val textFieldEnabled: Boolean,
        val sendButtonEnabled: Boolean,
        val listener: Listener,
    ) {
        @Immutable
        interface Listener {
            fun onTextChange(text: String)

            fun onClickSend()
        }
    }

    @Immutable
    interface Listener {
        fun onClickRetry()

        fun onClickBack()
    }
}
