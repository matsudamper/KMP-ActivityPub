package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.LocalCustomEmoji
import net.matsudamper.kotpub.emoji.EmojiObject

/**
 * このサーバーのカスタム絵文字を外から指す URL
 */
class CustomEmojiUrls(
    private val domain: String,
) {
    /**
     * 他のサーバーは URL ごと画像を覚えるので、画像を差し替えると [publicId] ごと変わる
     */
    fun imageUrl(publicId: String): String = "https://$domain$IMAGE_PATH_PREFIX/$publicId"

    fun emojiObjectOf(emoji: LocalCustomEmoji): EmojiObject = EmojiObject(
        id = "https://$domain$OBJECT_PATH_PREFIX/${emoji.shortcode}",
        name = ":${emoji.shortcode}:",
        icon = EmojiObject.Icon(url = imageUrl(emoji.publicId), mediaType = emoji.contentType),
    )

    companion object {
        const val IMAGE_PATH_PREFIX: String = "/custom-emojis"
        const val OBJECT_PATH_PREFIX: String = "/emojis"
    }
}
