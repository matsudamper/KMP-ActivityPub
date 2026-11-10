package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.NotePinRepository
import net.matsudamper.kmp.activitypub.repository.PinNoteResult
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_PINS
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

internal class SqliteNotePinRepository(
    private val jooq: SqliteJooq,
) : NotePinRepository {
    override fun pin(
        accountId: AccountId,
        publicId: PublicNoteId,
        pinnedAt: Instant,
        maxPins: Int,
    ): PinNoteResult = jooq.transaction { dsl ->
        val noteId = dsl.select(NOTES.ID)
            .from(NOTES)
            .where(NOTES.PUBLIC_ID.eq(publicId.value))
            .and(NOTES.ACCOUNT_ID.eq(accountId.value))
            .fetchOne(NOTES.ID)
            ?: return@transaction PinNoteResult.NotFound
        if (dsl.fetchExists(NOTE_PINS, NOTE_PINS.NOTE_ID.eq(noteId))) return@transaction PinNoteResult.Pinned
        val pinCount = dsl.selectCount()
            .from(NOTE_PINS)
            .join(NOTES).on(NOTES.ID.eq(NOTE_PINS.NOTE_ID))
            .where(NOTES.ACCOUNT_ID.eq(accountId.value))
            .fetchOne(0, Int::class.java)
            ?: 0
        if (pinCount >= maxPins) return@transaction PinNoteResult.TooManyPins
        dsl.insertInto(NOTE_PINS)
            .set(NOTE_PINS.NOTE_ID, noteId)
            .set(NOTE_PINS.PINNED_AT, StoredInstant.format(pinnedAt))
            .execute()
        PinNoteResult.Pinned
    }

    override fun unpin(
        accountId: AccountId,
        publicId: PublicNoteId,
    ): Boolean = jooq.withConnection { dsl ->
        dsl.deleteFrom(NOTE_PINS)
            .where(
                NOTE_PINS.NOTE_ID.`in`(
                    dsl.select(NOTES.ID)
                        .from(NOTES)
                        .where(NOTES.PUBLIC_ID.eq(publicId.value))
                        .and(NOTES.ACCOUNT_ID.eq(accountId.value)),
                ),
            )
            .execute() > 0
    }

    override fun pinnedNotes(username: String): List<PublicNoteId> = jooq.withConnection { dsl ->
        dsl.select(NOTES.PUBLIC_ID)
            .from(NOTE_PINS)
            .join(NOTES).on(NOTES.ID.eq(NOTE_PINS.NOTE_ID))
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(NOTES.ACCOUNT_ID))
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
            .orderBy(NOTE_PINS.PINNED_AT.desc(), NOTES.ID.desc())
            .fetch { PublicNoteId(checkNotNull(it.value1())) }
    }
}
