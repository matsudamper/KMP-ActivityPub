package net.matsudamper.kmp.activitypub.frontend.screen.image

import androidx.compose.runtime.Immutable

data class ImageViewerScreenUiState(
    val imageUrl: String,
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onClickClose()

        fun onClickOutsideImage()
    }
}
