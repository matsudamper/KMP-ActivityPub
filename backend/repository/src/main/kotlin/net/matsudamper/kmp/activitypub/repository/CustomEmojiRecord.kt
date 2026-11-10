package net.matsudamper.kmp.activitypub.repository

/**
 * 他のサーバーのカスタム絵文字。本文や表示名には [name] の文字しか入らないので、画像を一緒に持つ
 *
 * @param name `:name:` の形
 * @param imageUrl 他のサーバーにある画像の URL
 */
data class CustomEmojiRecord(
    val name: String,
    val imageUrl: String,
)
