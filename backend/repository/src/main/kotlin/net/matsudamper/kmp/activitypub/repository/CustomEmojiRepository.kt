package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.CustomEmojiId

/**
 * このサーバーで置いたカスタム絵文字。サーバーの全員が使え、消せるのは置いたアカウントだけ
 */
interface CustomEmojiRepository {
    /**
     * @return 同じ shortcode が既にあれば null
     */
    fun add(emoji: NewLocalCustomEmoji): LocalCustomEmoji?

    /**
     * shortcode の順
     */
    fun all(): List<LocalCustomEmoji>

    fun findByShortcode(shortcode: String): LocalCustomEmoji?

    fun findByPublicId(publicId: String): LocalCustomEmoji?

    /**
     * @return 消した絵文字。無いか、[accountId] が置いたものでなければ null
     */
    fun delete(id: CustomEmojiId, accountId: AccountId): LocalCustomEmoji?
}

/**
 * @param shortcode コロンを含めない名前
 * @param publicId 画像の URL に入る識別子
 * @param path 画像の置き場の中での場所
 */
data class NewLocalCustomEmoji(
    val shortcode: String,
    val publicId: String,
    val path: String,
    val contentType: String,
    val uploadedBy: AccountId,
    val createdAt: Instant,
)

/**
 * @param shortcode コロンを含めない名前
 * @param publicId 画像の URL に入る識別子
 * @param path 画像の置き場の中での場所
 */
data class LocalCustomEmoji(
    val id: CustomEmojiId,
    val shortcode: String,
    val publicId: String,
    val path: String,
    val contentType: String,
    val uploadedBy: AccountId,
    val createdAt: Instant,
)
