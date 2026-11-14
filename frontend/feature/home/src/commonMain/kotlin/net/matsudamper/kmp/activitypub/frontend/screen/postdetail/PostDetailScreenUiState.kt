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

        /**
         * @param replyTargets 返信先を、古い順に並べたもの。最後が [post] の直接の返信先
         */
        data class Loaded(
            val replyTargetLoader: ReplyTargetLoader?,
            val replyTargets: List<PostItemUiState>,
            val post: PostItemUiState,
            val mentionBox: MentionBox,
        ) : Content

        data object NotFound : Content

        data object Error : Content
    }

    /**
     * 返信先の一番上に置く、さらに上の返信先を取る行。たどる返信先が無ければ出さない
     */
    sealed interface ReplyTargetLoader {
        /**
         * 表示されたら次の返信先を取りに行く
         */
        data class Loading(val listener: Listener) : ReplyTargetLoader {
            @Immutable
            interface Listener {
                fun onVisible()
            }
        }

        /**
         * 消されたか、読めない返信先
         */
        data object Unavailable : ReplyTargetLoader

        data class Error(val listener: Listener) : ReplyTargetLoader {
            @Immutable
            interface Listener {
                fun onClickRetry()
            }
        }
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
