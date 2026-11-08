package net.matsudamper.kmp.activitypub.frontend.screen.image

import androidx.compose.runtime.Immutable

/**
 * @param initialIndex 最初に見せる [images] の位置
 */
data class ImageViewerScreenUiState(
    val images: List<Image>,
    val initialIndex: Int,
    val listener: Listener,
) {
    data class Image(
        val url: String,
        val listener: ImageListener,
    )

    @Immutable
    interface ImageListener {
        fun onClickConfirmDownload()
    }

    @Immutable
    interface Listener {
        fun onClickClose()

        fun onClickOutsideImage()
    }
}
