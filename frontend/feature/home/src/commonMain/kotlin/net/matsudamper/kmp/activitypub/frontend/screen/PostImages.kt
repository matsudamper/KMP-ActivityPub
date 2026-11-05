package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostImage
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

internal fun createPostImage(
    image: UserPostImage,
    sensitive: Boolean,
    sensitiveImagesBlurred: Boolean,
    onClick: () -> Unit,
): PostItemUiState.Image {
    return PostItemUiState.Image(
        url = image.url,
        description = image.description,
        blurToggleButtonVisible = sensitive,
        blurredInitially = sensitive && sensitiveImagesBlurred,
        listener = object : PostItemUiState.ImageListener {
            override fun onClick() {
                onClick()
            }
        },
    )
}
