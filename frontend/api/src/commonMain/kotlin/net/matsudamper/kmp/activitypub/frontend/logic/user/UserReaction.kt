package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * 投稿に付けるリアクションの絵文字
 */
sealed interface UserReaction {
    data class Unicode(val emoji: String) : UserReaction

    /**
     * @param shortcode このサーバーのカスタム絵文字の、コロンを含めない名前
     */
    data class Custom(val shortcode: String, val imageUrl: String) : UserReaction
}

/**
 * ログイン中のアカウントが投稿に付けたリアクション
 *
 * @param emoji 絵文字そのもの、またはカスタム絵文字の `:shortcode:`
 * @param imageUrl カスタム絵文字の画像。Unicode の絵文字か、消された絵文字なら null で、[emoji] の文字で見せる
 */
data class UserViewerReaction(
    val emoji: String,
    val imageUrl: String?,
)

fun UserReaction.toViewerReaction(): UserViewerReaction = when (this) {
    is UserReaction.Unicode -> UserViewerReaction(emoji = emoji, imageUrl = null)
    is UserReaction.Custom -> UserViewerReaction(emoji = ":$shortcode:", imageUrl = imageUrl)
}
