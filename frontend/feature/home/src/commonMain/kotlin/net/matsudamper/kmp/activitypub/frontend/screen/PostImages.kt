package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostImage
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

/**
 * @param blurToggled 一括の表示・非表示から、この画像だけ開け閉めして逆にしているか
 */
internal fun createPostImage(
    image: UserPostImage,
    sensitive: Boolean,
    sensitiveImagesBlurred: Boolean,
    blurToggled: Boolean,
    onOpen: () -> Unit,
    onToggleBlur: () -> Unit,
): PostItemUiState.Image {
    val blurred = sensitive && sensitiveImagesBlurred != blurToggled
    return PostItemUiState.Image(
        url = image.url,
        description = image.description,
        blurToggleButtonVisible = sensitive,
        blurred = blurred,
        listener = object : PostItemUiState.ImageListener {
            override fun onClick() {
                if (blurred) {
                    onToggleBlur()
                } else {
                    onOpen()
                }
            }

            override fun onClickBlurToggle() {
                onToggleBlur()
            }
        },
    )
}
