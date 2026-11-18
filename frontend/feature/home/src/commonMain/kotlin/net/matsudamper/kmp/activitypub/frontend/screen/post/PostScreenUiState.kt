package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.runtime.Immutable
import coil3.PlatformContext
import coil3.request.ImageRequest
import net.matsudamper.kmp.activitypub.frontend.screen.reaction.ReactionPickerDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.TextInsertion

data class PostScreenUiState(
    val body: String,
    /**
     * 選んだ絵文字。入力欄が本文に入れ終えるまで持つ。入れるものが無ければ null
     */
    val bodyInsertion: TextInsertion?,
    val contentWarningInputVisible: Boolean,
    val contentWarning: String,
    val contentWarningButtonChecked: Boolean,
    val contentWarningButtonEnabled: Boolean,
    val inputHasError: Boolean,
    val inputEnabled: Boolean,
    /**
     * 閉じると送信の結果を画面に出せないので、送信の途中は false になる
     */
    val closeButtonEnabled: Boolean,
    val postButtonEnabled: Boolean,
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

        fun onClickContentWarning()

        fun onContentWarningChanged(text: String)

        fun onClickAddImage()

        fun onImagePicked(file: PickedImageFile)

        fun onClickAddEmoji()

        /**
         * 開いたままの投稿カラムで、タイムラインの投稿への返信を始めるとき
         *
         * @param postId 返信するホームタイムラインの投稿の id
         */
        fun onReplyRequested(postId: String)

        fun onClickPost()

        fun onPressPostShortcut()

        fun onClickClose()
    }
}
