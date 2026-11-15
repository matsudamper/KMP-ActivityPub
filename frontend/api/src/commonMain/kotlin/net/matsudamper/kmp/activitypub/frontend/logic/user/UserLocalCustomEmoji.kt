package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * このサーバーで置いたカスタム絵文字
 *
 * @param shortcode コロンを含めない名前。本文やリアクションには `:shortcode:` で入る
 * @param deletable ログイン中のアカウントが置いた絵文字か。消せるのは置いたアカウントだけ
 */
data class UserLocalCustomEmoji(
    val id: String,
    val shortcode: String,
    val imageUrl: String,
    val deletable: Boolean,
)

sealed interface UserCustomEmojisResult {
    /**
     * @param emojis shortcode の順
     */
    data class Success(val emojis: List<UserLocalCustomEmoji>) : UserCustomEmojisResult

    data class Failure(val message: String) : UserCustomEmojisResult
}

sealed interface UserUploadCustomEmojiResult {
    data class Success(val emoji: UserLocalCustomEmoji) : UserUploadCustomEmojiResult

    data class Failure(val message: String) : UserUploadCustomEmojiResult
}
