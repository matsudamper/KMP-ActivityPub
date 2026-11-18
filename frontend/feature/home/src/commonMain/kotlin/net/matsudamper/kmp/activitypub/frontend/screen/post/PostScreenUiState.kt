package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.runtime.Immutable
import coil3.PlatformContext
import coil3.request.ImageRequest
import net.matsudamper.kmp.activitypub.frontend.screen.reaction.ReactionPickerDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.TextInsertion

data class PostScreenUiState(
    val title: String,
    val body: String,
    /**
     * 選んだ絵文字。入力欄が本文に入れ終えるまで持つ。入れるものが無ければ null
     */
    val bodyInsertion: TextInsertion?,
    /**
     * 編集する投稿の本文を読み込んでいる間
     */
    val bodyLoadingVisible: Boolean,
    val inputHasError: Boolean,
    val inputEnabled: Boolean,
    /**
     * 閉じると送信の結果を画面に出せないので、送信の途中は false になる
     */
    val closeButtonEnabled: Boolean,
    val postButtonEnabled: Boolean,
    val postButtonText: String,
    /**
     * 付けた順
     */
    val images: List<Image>,
    /**
     * 引用でなければ null
     */
    val quote: PostItemUiState.Quote?,
    /**
     * 返信でなければ null
     */
    val replyTarget: ReplyTarget?,
    /**
     * 投稿カラムで自分の投稿を編集している間。編集のために開いた画面や、新しく投稿するなら null
     */
    val editTarget: EditTarget?,
    /**
     * 書きかけの投稿を消して編集や返信を始めてよいか確かめる。確かめていなければ null
     */
    val discardDraftDialog: DiscardDraftDialog?,
    val addImageButtonEnabled: Boolean,
    val addImageLoadingVisible: Boolean,
    val addEmojiButtonEnabled: Boolean,
    /**
     * 絵文字を選んでいなければ null
     */
    val emojiPickerDialog: ReactionPickerDialogUiState?,
    val listener: Listener,
) {
    /**
     * @param preview 送る画像。表示するときに読む
     */
    data class Image(
        val preview: ImagePreview,
        val removeButtonEnabled: Boolean,
        val listener: ImageListener,
    )

    data class ReplyTarget(
        val post: PostItemUiState.Quote,
        val removeButtonEnabled: Boolean,
        val listener: ReplyTargetListener,
    )

    @Immutable
    interface ReplyTargetListener {
        fun onClickRemove()
    }

    data class EditTarget(
        val cancelButtonEnabled: Boolean,
        val listener: EditTargetListener,
    )

    @Immutable
    interface EditTargetListener {
        fun onClickCancel()
    }

    data class DiscardDraftDialog(
        val message: String,
        val discardButtonText: String,
        val listener: DiscardDraftDialogListener,
    )

    @Immutable
    interface DiscardDraftDialogListener {
        fun onClickDiscard()

        fun onDismiss()
    }

    @Immutable
    fun interface ImagePreview {
        fun imageRequest(context: PlatformContext): ImageRequest
    }

    @Immutable
    interface ImageListener {
        fun onClickRemove()
    }

    @Immutable
    interface Listener {
        fun onBodyChanged(text: String)

        fun onBodyInsertionHandled()

        fun onClickAddImage()

        fun onImagePicked(file: PickedImageFile)

        fun onClickAddEmoji()

        /**
         * 開いたままの投稿カラムで、タイムラインの投稿への返信を始めるとき
         *
         * @param postId 返信するホームタイムラインの投稿の id
         */
        fun onReplyRequested(postId: String)

        /**
         * 開いたままの投稿カラムで、自分の投稿の編集を始めるとき
         *
         * @param noteId 編集する投稿の deletableNoteId
         */
        fun onEditRequested(noteId: String)

        fun onClickPost()

        fun onPressPostShortcut()

        fun onClickClose()
    }
}
