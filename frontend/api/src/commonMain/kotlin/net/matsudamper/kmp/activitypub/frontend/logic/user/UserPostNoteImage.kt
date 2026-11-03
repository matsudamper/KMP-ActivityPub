package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * @param bytes 送る画像。形式はサーバーが判定する
 * @param description 画像の説明文。無ければ null
 */
class UserPostNoteImage(
    val bytes: ByteArray,
    val description: String?,
)
