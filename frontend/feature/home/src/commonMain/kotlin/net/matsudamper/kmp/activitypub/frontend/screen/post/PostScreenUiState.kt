package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.runtime.Immutable
import coil3.PlatformContext
import coil3.request.ImageRequest
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile

data class PostScreenUiState(
    val body: String,
    val inputHasError: Boolean,
    val inputEnabled: Boolean,
    /**
     * 閉じると送信の結果を画面に出せないので、送信の途中は false になる
     */
    val backButtonEnabled: Boolean,
    val postButtonEnabled: Boolean,
    /**
     * 付けた順
     */
    val images: List<Image>,
    val addImageButtonEnabled: Boolean,
    val addImageLoadingVisible: Boolean,
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

        fun onClickAddImage()

        fun onImagePicked(file: PickedImageFile)

        fun onClickPost()

        fun onPressPostShortcut()

        fun onClickBack()
    }
}
