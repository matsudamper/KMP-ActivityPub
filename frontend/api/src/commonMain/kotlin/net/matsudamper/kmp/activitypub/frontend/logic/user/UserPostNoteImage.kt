package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * @param bytes PNG / JPEG / GIF / WebP の画像
 * @param description 画像の説明文。無ければ null
 */
class UserPostNoteImage(
    val bytes: ByteArray,
    val description: String?,
)
