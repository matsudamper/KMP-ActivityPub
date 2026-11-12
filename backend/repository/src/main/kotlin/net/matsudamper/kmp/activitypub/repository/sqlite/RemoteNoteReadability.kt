package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_FOLLOWING
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_BOOSTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTE_RECIPIENTS
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL

/**
 * ブーストを受け取って載せる投稿の公開範囲
 */
internal val PUBLISHED_REMOTE_NOTE_VISIBILITIES = listOf("public", "unlisted")

/**
 * こちらのアカウントがブーストできる投稿の公開範囲。公開タイムラインに載せない公開など、公開範囲に制限のある投稿はひとまずブーストできないようにする
 */
internal const val BOOSTABLE_REMOTE_NOTE_VISIBILITY = "public"

/**
 * REMOTE_NOTES の行を [accountId] が読めるか
 */
internal fun DSLContext.remoteNoteReadableBy(accountId: AccountId): Condition {
    val explicit = DSL.exists(
        selectOne().from(REMOTE_NOTE_RECIPIENTS).where(REMOTE_NOTE_RECIPIENTS.REMOTE_NOTE_ID.eq(REMOTE_NOTES.ID))
            .and(REMOTE_NOTE_RECIPIENTS.ACCOUNT_ID.eq(accountId.value)).and(REMOTE_NOTE_RECIPIENTS.KIND.eq("explicit")),
    )
    val follows = DSL.exists(
        selectOne().from(ACCOUNT_FOLLOWING).join(REMOTE_ACTORS).on(REMOTE_ACTORS.ACTOR_URI.eq(ACCOUNT_FOLLOWING.ACTOR_URI))
            .where(ACCOUNT_FOLLOWING.ACCOUNT_ID.eq(accountId.value)).and(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
            .and(REMOTE_NOTES.VISIBILITY.ne("followers").or(ACCOUNT_FOLLOWING.ACCEPTED_AT.isNotNull)),
    )
    val followerRecipient = DSL.exists(
        selectOne().from(REMOTE_NOTE_RECIPIENTS).where(REMOTE_NOTE_RECIPIENTS.REMOTE_NOTE_ID.eq(REMOTE_NOTES.ID))
            .and(REMOTE_NOTE_RECIPIENTS.ACCOUNT_ID.eq(accountId.value)).and(REMOTE_NOTE_RECIPIENTS.KIND.eq("followers")),
    )
    return explicit.or(follows.and(REMOTE_NOTES.VISIBILITY.`in`("public", "unlisted").or(REMOTE_NOTES.VISIBILITY.eq("followers").and(followerRecipient))))
}

/**
 * REMOTE_BOOSTS の行を [accountId] のタイムラインに載せてよいか。ブーストした相手をフォローしていれば、投稿者のフォローは問わない
 */
internal fun DSLContext.remoteBoostReadableBy(accountId: AccountId): Condition =
    REMOTE_NOTES.VISIBILITY.`in`(PUBLISHED_REMOTE_NOTE_VISIBILITIES)
        .andExists(
            selectOne().from(ACCOUNT_FOLLOWING).join(REMOTE_ACTORS).on(REMOTE_ACTORS.ACTOR_URI.eq(ACCOUNT_FOLLOWING.ACTOR_URI))
                .where(ACCOUNT_FOLLOWING.ACCOUNT_ID.eq(accountId.value)).and(REMOTE_ACTORS.ID.eq(REMOTE_BOOSTS.REMOTE_ACTOR_ID)),
        )

/**
 * REMOTE_NOTES の行を [accountId] が読めるか、フォローしている相手のブーストで [accountId] のタイムラインに載っているか。
 * ブーストで知った投稿は投稿者をフォローしていなくても、いいねやブーストを付けられる
 */
internal fun DSLContext.remoteNoteReadableOrBoostedTo(accountId: AccountId): Condition =
    remoteNoteReadableBy(accountId).orExists(
        selectOne().from(REMOTE_BOOSTS).where(REMOTE_BOOSTS.REMOTE_NOTE_ID.eq(REMOTE_NOTES.ID)).and(remoteBoostReadableBy(accountId)),
    )

/**
 * 投稿者が消した投稿に印を付ける。タイムラインに削除済みと出すので行は残す。投稿が [actorUri] のものでなければ何もしない
 *
 * @return 削除済みになったタイムラインの member。投稿そのものと、それをブーストした行。保存していないか、既に消されていれば空
 */
internal fun DSLContext.markRemoteNoteDeleted(actorUri: String, objectUri: String, deletedAt: Instant): List<String> {
    val noteId = select(REMOTE_NOTES.ID).from(REMOTE_NOTES)
        .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
        .where(REMOTE_NOTES.OBJECT_URI.eq(objectUri)).and(REMOTE_ACTORS.ACTOR_URI.eq(actorUri)).and(REMOTE_NOTES.DELETED_AT.isNull)
        .fetchOne(REMOTE_NOTES.ID) ?: return listOf()
    update(REMOTE_NOTES).set(REMOTE_NOTES.DELETED_AT, StoredInstant.format(deletedAt)).where(REMOTE_NOTES.ID.eq(noteId)).execute()
    val boostMembers = select(REMOTE_BOOSTS.ID).from(REMOTE_BOOSTS).where(REMOTE_BOOSTS.REMOTE_NOTE_ID.eq(noteId))
        .fetch { "b${it.value1()}" }
    return listOf("r$noteId") + boostMembers
}
