package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_FOLLOWING
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTE_RECIPIENTS
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL

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
