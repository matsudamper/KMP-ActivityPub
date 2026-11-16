package net.matsudamper.kmp.activitypub.frontend.screen.postdetail

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.screen.reaction.ReactionPickerDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PostRevisionsDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ReactionConfirmDialogUiState

data class PostDetailScreenUiState(
    val content: Content,
    /**
     * 出していなければ null
     */
    val deleteNoteDialog: DeleteNoteDialogUiState?,
    val reactionPickerDialog: ReactionPickerDialogUiState?,
    val reactionConfirmDialog: ReactionConfirmDialogUiState?,
    val postRevisionsDialog: PostRevisionsDialogUiState?,
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
            val replies: Replies,
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
     * 開いている投稿の下に並べる返信
     */
    sealed interface Replies {
        /**
         * 古い順。取り直している間と取り直せなかったときは、前に取れたものを出したままにする
         */
        val items: List<Reply>

        data class Loading(override val items: List<Reply>) : Replies

        data class Loaded(override val items: List<Reply>) : Replies

        data class Error(
            override val items: List<Reply>,
            val listener: Listener,
        ) : Replies {
            @Immutable
            interface Listener {
                fun onClickRetry()
            }
        }
    }

    /**
     * @param listKey 取り直しで返信が増減しても、画像の横スクロールの位置などが別の返信に移らないよう、一覧の中で返信を見分ける
     */
    data class Reply(
        val listKey: String,
        val post: PostItemUiState,
    )

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

        fun onClickClose()
    }
}
