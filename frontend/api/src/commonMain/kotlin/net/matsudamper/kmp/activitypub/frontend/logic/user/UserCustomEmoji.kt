package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * 他のサーバーのカスタム絵文字。文字列の中の [name] を [imageUrl] の画像に置き換えて見せる
 *
 * @param name `:name:` の形
 */
data class UserCustomEmoji(
    val name: String,
    val imageUrl: String,
)
