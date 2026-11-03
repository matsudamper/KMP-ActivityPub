package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
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
    data class Image(
        val bitmap: ImageBitmap,
        val removeButtonEnabled: Boolean,
        val listener: ImageListener,
    )

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

        fun onClickBack()
    }
}
