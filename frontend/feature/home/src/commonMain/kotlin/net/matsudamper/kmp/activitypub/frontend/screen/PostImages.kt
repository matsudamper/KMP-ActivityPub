package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostImage
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

internal fun createPostImage(
    image: UserPostImage,
    onClick: () -> Unit,
): PostItemUiState.Image {
    return PostItemUiState.Image(
        url = image.url,
        description = image.description,
        listener = object : PostItemUiState.ImageListener {
            override fun onClick() {
                onClick()
            }
        },
    )
}
