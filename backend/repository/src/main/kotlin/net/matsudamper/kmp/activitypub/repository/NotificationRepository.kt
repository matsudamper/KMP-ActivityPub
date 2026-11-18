package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.CustomEmojiId
import net.matsudamper.kmp.activitypub.shared.NotificationGroupId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * 通知。通知カラムの 1 項目（[NotificationGroup]）ごとに、相手ごとの記録（[NotificationEntry]）を持つ。
 * お気に入りとブーストは投稿ごと・種類ごとに 1 項目にまとめ、フォローとメンションは 1 件ごとに 1 項目にする
 */
interface NotificationRepository {
    /**
     * 同じ相手から同じものが届き直しても記録を増やさない。お気に入りは絵文字だけを書き換え、ほかは何もしない
     *
     * @return 書き換えた項目。何も変えなかったか、相手が自分自身か、対象の投稿やアカウントが無ければ null
     */
    fun record(event: NotificationEvent): NotificationGroupId?

    /**
     * 新しい相手が加わった新しい順に、[before] より後ろから [limit] 件まで返す
     */
    fun page(accountId: AccountId, before: NotificationGroupPosition?, limit: Int): List<NotificationGroup>

    /**
     * 無ければ null
     */
    fun find(id: NotificationGroupId): NotificationGroup?
}

enum class NotificationKind {
    FOLLOW,
    MENTION,
    FAVOURITE,
    BOOST,
}

/**
 * 通知の元になった出来事
 */
sealed interface NotificationEvent {
    val actor: NotificationActorRef
    val occurredAt: Instant

    data class Followed(
        val followeeUsername: String,
        override val actor: NotificationActorRef,
        override val occurredAt: Instant,
    ) : NotificationEvent

    /**
     * 宛先だけを指定した投稿 (DM) では通知しない。呼ぶ側で除く
     */
    data class Mentioned(
        val mentionedUsername: String,
        val post: NotificationPostRef,
        override val actor: NotificationActorRef,
        override val occurredAt: Instant,
    ) : NotificationEvent

    /**
     * @param reaction 付けた絵文字。絵文字の無いいいねでは null
     */
    data class Favourited(
        val notePublicId: PublicNoteId,
        val reaction: NotificationReactionInput?,
        override val actor: NotificationActorRef,
        override val occurredAt: Instant,
    ) : NotificationEvent

    data class Boosted(
        val notePublicId: PublicNoteId,
        override val actor: NotificationActorRef,
        override val occurredAt: Instant,
    ) : NotificationEvent
}

sealed interface NotificationActorRef {
    data class Local(val accountId: AccountId) : NotificationActorRef

    /**
     * 記録済みのアクターだけを指せる
     */
    data class Remote(val actorUri: String) : NotificationActorRef
}

sealed interface NotificationPostRef {
    data class Local(val publicId: PublicNoteId) : NotificationPostRef

    data class Remote(val objectUri: String) : NotificationPostRef
}

/**
 * @param emoji Unicode の絵文字そのものか :name:
 * @param emojiImageUrl 他のサーバーのカスタム絵文字の画像の URL
 * @param customEmojiId このサーバーのカスタム絵文字
 */
data class NotificationReactionInput(
    val emoji: String,
    val emojiImageUrl: String?,
    val customEmojiId: CustomEmojiId?,
)

/**
 * 通知カラムの続きを指す位置
 */
data class NotificationGroupPosition(
    val updatedAt: Instant,
    val id: NotificationGroupId,
)

/**
 * @param accountId 通知を受け取るアカウント
 * @param noteMember 対象の投稿のタイムラインの member。フォローでは null
 * @param entries 相手ごとの記録。相手が最初に届いた新しい順
 * @param updatedAt 最後に新しい相手が加わった時刻
 */
data class NotificationGroup(
    val id: NotificationGroupId,
    val accountId: AccountId,
    val kind: NotificationKind,
    val noteMember: String?,
    val entries: List<NotificationEntry>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val position: NotificationGroupPosition get() = NotificationGroupPosition(updatedAt = updatedAt, id = id)
}

/**
 * @param reaction お気に入りで付けた絵文字。絵文字の無いいいねと、ほかの種類では null
 * @param createdAt その相手から最初に届いた時刻
 * @param updatedAt 最後に書き換えた時刻
 */
data class NotificationEntry(
    val actor: NotificationActor,
    val reaction: NotificationReaction?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * @param actorUri 他のサーバーのアクターの URI。このサーバーのアカウントは null
 * @param username 相手の preferredUsername。無ければアクターの URI
 * @param iconUrl 他のサーバーのアクターのプロフィール画像の URL。このサーバーのアカウントは null
 * @param emojis 他のサーバーのアクターの表示名に入るカスタム絵文字。このサーバーのアカウントは空
 * @param updatedAt 他のサーバーのアクターの情報を最後に受け取った時刻。このサーバーのアカウントは null
 */
data class NotificationActor(
    val actorUri: String?,
    val username: String,
    val displayName: String?,
    val iconUrl: String?,
    val emojis: List<CustomEmojiRecord>,
    val updatedAt: Instant?,
)

/**
 * @param emojiImageUrl 他のサーバーのカスタム絵文字の画像の URL。Unicode の絵文字と、このサーバーのカスタム絵文字では null
 * @param localCustomEmojiPublicId このサーバーのカスタム絵文字なら、画像の公開 id。消された絵文字と、それ以外では null
 */
data class NotificationReaction(
    val emoji: String,
    val emojiImageUrl: String?,
    val localCustomEmojiPublicId: String?,
)
