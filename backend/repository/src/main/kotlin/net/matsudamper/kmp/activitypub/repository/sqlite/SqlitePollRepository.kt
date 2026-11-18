package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.PollOptionRecord
import net.matsudamper.kmp.activitypub.repository.PollRecord
import net.matsudamper.kmp.activitypub.repository.PollRepository
import net.matsudamper.kmp.activitypub.repository.PollVoteInput
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_POLLS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_POLL_OPTIONS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_POLL_VOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTE_POLLS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTE_POLL_OPTIONS
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.jooq.DSLContext
import org.jooq.impl.DSL

internal class SqlitePollRepository(
    private val jooq: SqliteJooq,
) : PollRepository {
    override fun localPolls(publicIds: Set<PublicNoteId>): Map<PublicNoteId, PollRecord> {
        if (publicIds.isEmpty()) return mapOf()
        return jooq.withConnection { dsl ->
            val polls = dsl.select(NOTES.ID, NOTES.PUBLIC_ID, NOTE_POLLS.MULTIPLE_CHOICE, NOTE_POLLS.ENDS_AT)
                .from(NOTE_POLLS)
                .join(NOTES).on(NOTES.ID.eq(NOTE_POLLS.NOTE_ID))
                .where(NOTES.PUBLIC_ID.`in`(publicIds.map { it.value }))
                .fetch()
            val noteIds = polls.map { checkNotNull(it.value1()) }
            val voteCounts = dsl.select(NOTE_POLL_VOTES.NOTE_ID, NOTE_POLL_VOTES.POSITION, DSL.count())
                .from(NOTE_POLL_VOTES)
                .where(NOTE_POLL_VOTES.NOTE_ID.`in`(noteIds))
                .groupBy(NOTE_POLL_VOTES.NOTE_ID, NOTE_POLL_VOTES.POSITION)
                .fetch()
                .associate { (checkNotNull(it.value1()) to checkNotNull(it.value2())) to it.value3().toLong() }
            val votersCounts = dsl.select(NOTE_POLL_VOTES.NOTE_ID, DSL.countDistinct(NOTE_POLL_VOTES.ACTOR_URI))
                .from(NOTE_POLL_VOTES)
                .where(NOTE_POLL_VOTES.NOTE_ID.`in`(noteIds))
                .groupBy(NOTE_POLL_VOTES.NOTE_ID)
                .fetch()
                .associate { checkNotNull(it.value1()) to it.value2().toLong() }
            val optionsByNoteId = dsl.select(NOTE_POLL_OPTIONS.NOTE_ID, NOTE_POLL_OPTIONS.POSITION, NOTE_POLL_OPTIONS.NAME)
                .from(NOTE_POLL_OPTIONS)
                .where(NOTE_POLL_OPTIONS.NOTE_ID.`in`(noteIds))
                .orderBy(NOTE_POLL_OPTIONS.NOTE_ID, NOTE_POLL_OPTIONS.POSITION)
                .fetch()
                .groupBy(
                    keySelector = { checkNotNull(it.value1()) },
                    valueTransform = { option ->
                        val noteId = checkNotNull(option.value1())
                        PollOptionRecord(
                            name = checkNotNull(option.value3()),
                            votesCount = voteCounts[noteId to checkNotNull(option.value2())] ?: 0L,
                        )
                    },
                )
            polls.associate { poll ->
                val noteId = checkNotNull(poll.value1())
                PublicNoteId(checkNotNull(poll.value2())) to PollRecord(
                    options = optionsByNoteId[noteId].orEmpty(),
                    multipleChoice = poll.value3() == 1L,
                    endsAt = StoredInstant.parse(checkNotNull(poll.value4())),
                    votersCount = votersCounts[noteId] ?: 0L,
                )
            }
        }
    }

    override fun remotePolls(objectUris: Set<String>): Map<String, PollRecord> {
        if (objectUris.isEmpty()) return mapOf()
        return jooq.withConnection { dsl ->
            val polls = dsl.select(REMOTE_NOTES.ID, REMOTE_NOTES.OBJECT_URI, REMOTE_NOTE_POLLS.MULTIPLE_CHOICE, REMOTE_NOTE_POLLS.ENDS_AT, REMOTE_NOTE_POLLS.VOTERS_COUNT)
                .from(REMOTE_NOTE_POLLS)
                .join(REMOTE_NOTES).on(REMOTE_NOTES.ID.eq(REMOTE_NOTE_POLLS.REMOTE_NOTE_ID))
                .where(REMOTE_NOTES.OBJECT_URI.`in`(objectUris))
                .fetch()
            val optionsByNoteId = dsl.remotePollOptions(polls.map { checkNotNull(it.value1()) })
            polls.associate { poll ->
                checkNotNull(poll.value2()) to PollRecord(
                    options = optionsByNoteId[poll.value1()].orEmpty(),
                    multipleChoice = poll.value3() == 1L,
                    endsAt = poll.value4()?.let { StoredInstant.parse(it) },
                    votersCount = poll.value5(),
                )
            }
        }
    }

    override fun recordVote(vote: PollVoteInput): Boolean = jooq.transaction { dsl ->
        val poll = dsl.select(NOTES.ID, NOTE_POLLS.MULTIPLE_CHOICE, NOTE_POLLS.ENDS_AT)
            .from(NOTE_POLLS)
            .join(NOTES).on(NOTES.ID.eq(NOTE_POLLS.NOTE_ID))
            .where(NOTES.PUBLIC_ID.eq(vote.publicId.value))
            .fetchOne() ?: return@transaction false
        val noteId = checkNotNull(poll.value1())
        if (!vote.votedAt.isBefore(StoredInstant.parse(checkNotNull(poll.value3())))) return@transaction false
        val position = dsl.select(NOTE_POLL_OPTIONS.POSITION)
            .from(NOTE_POLL_OPTIONS)
            .where(NOTE_POLL_OPTIONS.NOTE_ID.eq(noteId))
            .and(NOTE_POLL_OPTIONS.NAME.eq(vote.choice))
            .orderBy(NOTE_POLL_OPTIONS.POSITION)
            .limit(1)
            .fetchOne(NOTE_POLL_OPTIONS.POSITION) ?: return@transaction false
        val multipleChoice = poll.value2() == 1L
        if (!multipleChoice) {
            val alreadyVoted = dsl.fetchExists(
                dsl.selectOne().from(NOTE_POLL_VOTES)
                    .where(NOTE_POLL_VOTES.NOTE_ID.eq(noteId))
                    .and(NOTE_POLL_VOTES.ACTOR_URI.eq(vote.actorUri)),
            )
            if (alreadyVoted) return@transaction false
        }
        dsl.insertInto(NOTE_POLL_VOTES)
            .set(NOTE_POLL_VOTES.NOTE_ID, noteId)
            .set(NOTE_POLL_VOTES.POSITION, position)
            .set(NOTE_POLL_VOTES.ACTOR_URI, vote.actorUri)
            .set(NOTE_POLL_VOTES.VOTE_URI, vote.voteUri)
            .set(NOTE_POLL_VOTES.VOTED_AT, StoredInstant.format(vote.votedAt))
            .onConflictDoNothing()
            .execute() > 0
    }
}

private fun DSLContext.remotePollOptions(remoteNoteIds: List<Long>): Map<Long, List<PollOptionRecord>> =
    select(REMOTE_NOTE_POLL_OPTIONS.REMOTE_NOTE_ID, REMOTE_NOTE_POLL_OPTIONS.NAME, REMOTE_NOTE_POLL_OPTIONS.VOTES_COUNT)
        .from(REMOTE_NOTE_POLL_OPTIONS)
        .where(REMOTE_NOTE_POLL_OPTIONS.REMOTE_NOTE_ID.`in`(remoteNoteIds))
        .orderBy(REMOTE_NOTE_POLL_OPTIONS.REMOTE_NOTE_ID, REMOTE_NOTE_POLL_OPTIONS.POSITION)
        .fetch()
        .groupBy(
            keySelector = { checkNotNull(it.value1()) },
            valueTransform = { PollOptionRecord(name = checkNotNull(it.value2()), votesCount = it.value3()) },
        )

/**
 * 他のサーバーの投稿の投票。投票でなければ null
 */
internal fun DSLContext.remoteNotePoll(remoteNoteId: Long): PollRecord? {
    val poll = select(REMOTE_NOTE_POLLS.MULTIPLE_CHOICE, REMOTE_NOTE_POLLS.ENDS_AT, REMOTE_NOTE_POLLS.VOTERS_COUNT)
        .from(REMOTE_NOTE_POLLS)
        .where(REMOTE_NOTE_POLLS.REMOTE_NOTE_ID.eq(remoteNoteId))
        .fetchOne() ?: return null
    return PollRecord(
        options = remotePollOptions(listOf(remoteNoteId))[remoteNoteId].orEmpty(),
        multipleChoice = poll.value1() == 1L,
        endsAt = poll.value2()?.let { StoredInstant.parse(it) },
        votersCount = poll.value3(),
    )
}

/**
 * 他のサーバーの投稿の投票を、届いた投稿の中身で置き換える。投票でなくなっていれば消す
 */
internal fun DSLContext.replaceRemoteNotePoll(remoteNoteId: Long, poll: PollRecord?) {
    deleteFrom(REMOTE_NOTE_POLL_OPTIONS).where(REMOTE_NOTE_POLL_OPTIONS.REMOTE_NOTE_ID.eq(remoteNoteId)).execute()
    deleteFrom(REMOTE_NOTE_POLLS).where(REMOTE_NOTE_POLLS.REMOTE_NOTE_ID.eq(remoteNoteId)).execute()
    if (poll == null) return
    insertInto(REMOTE_NOTE_POLLS)
        .set(REMOTE_NOTE_POLLS.REMOTE_NOTE_ID, remoteNoteId)
        .set(REMOTE_NOTE_POLLS.MULTIPLE_CHOICE, if (poll.multipleChoice) 1L else 0L)
        .set(REMOTE_NOTE_POLLS.ENDS_AT, poll.endsAt?.let { StoredInstant.format(it) })
        .set(REMOTE_NOTE_POLLS.VOTERS_COUNT, poll.votersCount)
        .execute()
    poll.options.forEachIndexed { position, option ->
        insertInto(REMOTE_NOTE_POLL_OPTIONS)
            .set(REMOTE_NOTE_POLL_OPTIONS.REMOTE_NOTE_ID, remoteNoteId)
            .set(REMOTE_NOTE_POLL_OPTIONS.POSITION, position.toLong())
            .set(REMOTE_NOTE_POLL_OPTIONS.NAME, option.name)
            .set(REMOTE_NOTE_POLL_OPTIONS.VOTES_COUNT, option.votesCount)
            .execute()
    }
}
