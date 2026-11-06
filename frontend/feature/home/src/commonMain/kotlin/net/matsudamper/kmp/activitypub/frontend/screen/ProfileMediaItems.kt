package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.logic.user.UserProfileResult
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaUiState

/**
 * プロフィールの投稿に付いた画像を、投稿の順・付いた順に並べる
 */
internal fun createProfileMedia(
    posts: List<UserProfileResult.Post>,
    onOpenPost: (postId: String) -> Unit,
): List<ProfileMediaUiState> {
    return posts.flatMap { post ->
        val postId = post.postId
        val listener = if (postId == null) {
            null
        } else {
            object : ProfileMediaUiState.Listener {
                override fun onClick() {
                    onOpenPost(postId)
                }
            }
        }
        post.body.images.map { image ->
            ProfileMediaUiState(
                url = image.url,
                description = image.description,
                blurred = post.body.sensitive,
                listener = listener,
            )
        }
    }
}
