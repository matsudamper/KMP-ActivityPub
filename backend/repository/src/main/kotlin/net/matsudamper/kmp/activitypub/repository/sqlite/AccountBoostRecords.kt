package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_BOOSTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_BOOST_INBOXES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.DELIVERY_QUEUE
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL

/**
 * account_boosts の audience に入れる綴り。DB の CHECK では縛らないので、取りうる値はここだけが持つ
 */
internal object AccountBoostValues {
    const val AUDIENCE_PUBLIC = "public"
    const val AUDIENCE_UNLISTED = "unlisted"
}

/**
 * [condition] に合うこのサーバーのアカウントのブーストをすべて外し、Announce を積んだ宛先へ Undo を積む。
 * 投稿を消す処理と同じトランザクションで呼ぶ。投稿を消すとブーストの記録も消え、Undo を積めなくなる
 *
 * @return 外したブーストの数
 */
internal fun DSLContext.undoAccountBoosts(condition: Condition, now: Instant): Int {
    val boostIds = select(ACCOUNT_BOOSTS.ID).from(ACCOUNT_BOOSTS).where(condition).fetch(ACCOUNT_BOOSTS.ID).filterNotNull()
    return boostIds.count { undoAccountBoost(it, now) }
}

/**
 * ブーストを 1 件外す。まだ一度も送っていない Announce の宛先には、取り消すだけで Undo は積まない
 *
 * @return ブーストが無ければ false
 */
internal fun DSLContext.undoAccountBoost(boostId: Long, now: Instant): Boolean {
    val announcedInboxes = select(ACCOUNT_BOOST_INBOXES.INBOX).from(ACCOUNT_BOOST_INBOXES)
        .where(ACCOUNT_BOOST_INBOXES.BOOST_ID.eq(boostId))
        .fetch(ACCOUNT_BOOST_INBOXES.INBOX)
        .filterNotNull()
    val removed = deleteFrom(ACCOUNT_BOOSTS)
        .where(ACCOUNT_BOOSTS.ID.eq(boostId))
        .returning(ACCOUNT_BOOSTS.ACCOUNT_ID, ACCOUNT_BOOSTS.ANNOUNCE_ACTIVITY_URI, ACCOUNT_BOOSTS.UNDO_BODY)
        .fetchOne()
        ?: return false
    val accountId = AccountId(checkNotNull(removed.get(ACCOUNT_BOOSTS.ACCOUNT_ID)))
    val announceId = checkNotNull(removed.get(ACCOUNT_BOOSTS.ANNOUNCE_ACTIVITY_URI))
    val undoBody = checkNotNull(removed.get(ACCOUNT_BOOSTS.UNDO_BODY))
    val matchingAnnounce = DELIVERY_QUEUE.ACCOUNT_ID.eq(accountId.value)
        .and(DELIVERY_QUEUE.KIND.eq(DeliveryQueueValues.KIND_ANNOUNCE))
        .and(DSL.field("json_extract({0}, '$.id')", String::class.java, DELIVERY_QUEUE.BODY).eq(announceId))
    val unsentInboxes = deleteFrom(DELIVERY_QUEUE).where(matchingAnnounce)
        .and(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_PENDING))
        .and(DELIVERY_QUEUE.ATTEMPTS.eq(0L))
        .returning(DELIVERY_QUEUE.INBOX)
        .fetch(DELIVERY_QUEUE.INBOX)
        .filterNotNull()
        .toSet()
    // 送り直し待ちの Announce は相手に届いているかもしれないので、取り消した上で Undo も送る
    deleteFrom(DELIVERY_QUEUE).where(matchingAnnounce)
        .and(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_PENDING))
        .execute()
    // 送っている途中の行は消しても送信が止まらない。失敗したときに送り直して Undo の後に届かないよう、印を付ける
    update(DELIVERY_QUEUE)
        .set(DELIVERY_QUEUE.KIND, DeliveryQueueValues.KIND_CANCELLED_ANNOUNCE)
        .where(matchingAnnounce)
        .and(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_DELIVERING))
        .execute()
    val enqueuedAt = StoredInstant.format(now)
    announcedInboxes.filterNot { it in unsentInboxes }.forEach { inbox ->
        enqueueDelivery(accountId, inbox, undoBody, enqueuedAt, DeliveryQueueValues.KIND_UNDO_ANNOUNCE)
    }
    return true
}

internal fun DSLContext.enqueueDelivery(accountId: AccountId, inbox: String, body: String, now: String, kind: String) {
    insertInto(DELIVERY_QUEUE)
        .set(DELIVERY_QUEUE.KIND, kind)
        .set(DELIVERY_QUEUE.ACCOUNT_ID, accountId.value)
        .set(DELIVERY_QUEUE.INBOX, inbox)
        .set(DELIVERY_QUEUE.BODY, body)
        .set(DELIVERY_QUEUE.STATE, DeliveryQueueValues.STATE_PENDING)
        .set(DELIVERY_QUEUE.ATTEMPTS, 0L)
        .set(DELIVERY_QUEUE.NEXT_ATTEMPT_AT, now)
        .set(DELIVERY_QUEUE.ENQUEUED_AT, now)
        .execute()
}
