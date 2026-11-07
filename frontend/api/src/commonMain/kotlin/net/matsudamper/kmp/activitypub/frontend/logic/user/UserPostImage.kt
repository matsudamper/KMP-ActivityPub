package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * @param description 画像の説明文。無ければ null
 */
data class UserPostImage(
    val url: String,
    val thumbnailUrl: String,
    val description: String?,
)
