package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.NotificationId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * 他のサーバーから届いた、こちらの投稿へのお気に入りとブーストの通知。
 * 記録と取り消しは [FavouriteRepository] と [BoostRepository] がお気に入りやブーストと一緒に行う
 */
interface NotificationRepository {
    /**
     * 新しい順に、[after] より後ろから [limit] 件まで返す
     */
    fun page(accountId: AccountId, after: NotificationId?, limit: Int): List<ReceivedNotification>

    /**
     * 記録していなければ null
     */
    fun find(kind: NotificationKind, notePublicId: PublicNoteId, actorUri: String): ReceivedNotification?
}

enum class NotificationKind {
    FAVOURITE,
    BOOST,
}

/**
 * @param accountId 通知を受け取るアカウント。[noteMember] の投稿者
 * @param noteMember お気に入りかブーストをされた投稿の、タイムラインの member
 */
data class ReceivedNotification(
    val id: NotificationId,
    val accountId: AccountId,
    val kind: NotificationKind,
    val actor: NotificationActor,
    val noteMember: String,
    val receivedAt: Instant,
)

/**
 * @param username 相手の preferredUsername。無ければアクターの URI
 * @param updatedAt アクターの情報を最後に受け取った時刻
 */
data class NotificationActor(
    val actorUri: String,
    val username: String,
    val displayName: String?,
    val iconUrl: String?,
    val emojis: List<CustomEmojiRecord>,
    val updatedAt: Instant,
)
