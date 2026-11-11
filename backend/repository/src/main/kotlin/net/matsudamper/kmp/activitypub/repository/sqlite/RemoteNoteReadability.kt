package net.matsudamper.kmp.activitypub.repository.sqlite

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
 * 誰でも読める投稿の公開範囲。ブーストやプロフィールで知っただけの投稿は、この範囲のものだけ保存する
 */
internal val PUBLISHED_REMOTE_NOTE_VISIBILITIES = listOf("public", "unlisted")

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
 * REMOTE_NOTES の行を [accountId] が読めるか、誰でも読める公開範囲の投稿か
 */
internal fun DSLContext.remoteNoteReadableOrPublishedFor(accountId: AccountId): Condition =
    remoteNoteReadableBy(accountId).or(REMOTE_NOTES.VISIBILITY.`in`(PUBLISHED_REMOTE_NOTE_VISIBILITIES))

/**
 * REMOTE_BOOSTS の行を [accountId] のタイムラインに載せてよいか。ブーストした相手をフォローしていれば、投稿者のフォローは問わない
 */
internal fun DSLContext.remoteBoostReadableBy(accountId: AccountId): Condition =
    REMOTE_NOTES.VISIBILITY.`in`(PUBLISHED_REMOTE_NOTE_VISIBILITIES)
        .andExists(
            selectOne().from(ACCOUNT_FOLLOWING).join(REMOTE_ACTORS).on(REMOTE_ACTORS.ACTOR_URI.eq(ACCOUNT_FOLLOWING.ACTOR_URI))
                .where(ACCOUNT_FOLLOWING.ACCOUNT_ID.eq(accountId.value)).and(REMOTE_ACTORS.ID.eq(REMOTE_BOOSTS.REMOTE_ACTOR_ID)),
        )
