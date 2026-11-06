package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kmp.activitypub.repository.RemoteNoteReceptionInterest
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_FOLLOWING
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_IMAGES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_LIKES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTE_IMAGES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTE_LIKES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTE_RECIPIENTS
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL

internal class SqliteTimelineRepository(private val jooq: SqliteJooq) : TimelineRepository {
    private val remoteMember = DSL.inline("r").concat(REMOTE_NOTES.ID.cast(String::class.java))
    private val localMember = DSL.inline("l").concat(NOTES.ID.cast(String::class.java))

    override fun receptionInterest(actorUri: String, localRecipients: Set<String>): RemoteNoteReceptionInterest = jooq.withConnection { dsl ->
        RemoteNoteReceptionInterest(
            followingAccountCount = dsl.fetchCount(ACCOUNT_FOLLOWING, ACCOUNT_FOLLOWING.ACTOR_URI.eq(actorUri)).toLong(),
            localRecipientCount = dsl.fetchCount(ACCOUNTS, ACCOUNTS.USERNAME.`in`(localRecipients)).toLong(),
        )
    }

    override fun receive(note: RemoteNoteInput): TimelinePosition? = jooq.transaction { dsl ->
        val actorId = dsl.select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(note.actorUri))
            .fetchOne(REMOTE_ACTORS.ID) ?: return@transaction null
        val followers = dsl.select(ACCOUNT_FOLLOWING.ACCOUNT_ID, ACCOUNT_FOLLOWING.ACCEPTED_AT).from(ACCOUNT_FOLLOWING)
            .where(ACCOUNT_FOLLOWING.ACTOR_URI.eq(note.actorUri)).fetch()
        val addressed = dsl.select(ACCOUNTS.ID).from(ACCOUNTS).where(ACCOUNTS.USERNAME.`in`(note.localRecipients)).fetch(ACCOUNTS.ID)
        if (followers.isEmpty() && addressed.isEmpty()) return@transaction null
        val score = minOf(note.publishedAt, note.receivedAt).toEpochMilli()
        val inserted = dsl.insertInto(REMOTE_NOTES)
            .set(REMOTE_NOTES.OBJECT_URI, note.objectUri)
            .set(REMOTE_NOTES.REMOTE_ACTOR_ID, actorId)
            .set(REMOTE_NOTES.CONTENT_HTML, note.contentHtml)
            .set(REMOTE_NOTES.VISIBILITY, note.visibility)
            .set(REMOTE_NOTES.IN_REPLY_TO, note.inReplyTo)
            .set(REMOTE_NOTES.SUMMARY, note.summary)
            .set(REMOTE_NOTES.SENSITIVE, if (note.sensitive) 1L else 0L)
            .set(REMOTE_NOTES.PUBLISHED_AT, StoredInstant.format(note.publishedAt))
            .set(REMOTE_NOTES.RECEIVED_AT, StoredInstant.format(note.receivedAt))
            .set(REMOTE_NOTES.SCORE_MS, score)
            .onConflict(REMOTE_NOTES.OBJECT_URI).doNothing()
            .execute()
        if (inserted == 0) return@transaction null
        val id = checkNotNull(dsl.select(REMOTE_NOTES.ID).from(REMOTE_NOTES).where(REMOTE_NOTES.OBJECT_URI.eq(note.objectUri)).fetchOne(REMOTE_NOTES.ID))
        note.images.forEachIndexed { index, image ->
            dsl.insertInto(REMOTE_NOTE_IMAGES)
                .set(REMOTE_NOTE_IMAGES.REMOTE_NOTE_ID, id)
                .set(REMOTE_NOTE_IMAGES.POSITION, index.toLong())
                .set(REMOTE_NOTE_IMAGES.URL, image.url)
                .set(REMOTE_NOTE_IMAGES.DESCRIPTION, image.description)
                .execute()
        }
        addressed.forEach { accountId -> addRecipient(dsl, id, checkNotNull(accountId), "explicit", score) }
        if (note.visibility == "followers") {
            followers.filter { it.get(ACCOUNT_FOLLOWING.ACCEPTED_AT) != null }.forEach { follower ->
                addRecipient(dsl, id, checkNotNull(follower.get(ACCOUNT_FOLLOWING.ACCOUNT_ID)), "followers", score)
            }
        }
        TimelinePosition(score, "r$id")
    }

    override fun deleteRemote(actorUri: String, objectUri: String) {
        jooq.withConnection { dsl ->
            dsl.deleteFrom(REMOTE_NOTES).where(REMOTE_NOTES.OBJECT_URI.eq(objectUri))
                .and(REMOTE_NOTES.REMOTE_ACTOR_ID.`in`(dsl.select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))))
                .execute()
        }
    }

    override fun hasRemoteNote(objectUri: String): Boolean = jooq.withConnection { dsl ->
        dsl.fetchExists(REMOTE_NOTES, REMOTE_NOTES.OBJECT_URI.eq(objectUri))
    }

    override fun recipients(member: String, activeSince: Instant, after: AccountId?, limit: Int): List<AccountId> = jooq.withConnection { dsl ->
        val id = member.drop(1).toLong()
        if (member.startsWith("l")) {
            return@withConnection dsl.select(NOTES.ACCOUNT_ID).from(NOTES).where(NOTES.ID.eq(id))
                .and(NOTES.ACCOUNT_ID.gt(after?.value ?: 0L)).limit(limit)
                .fetch { AccountId(checkNotNull(it.value1())) }
        }
        val note = dsl.select(REMOTE_NOTES.REMOTE_ACTOR_ID, REMOTE_NOTES.VISIBILITY, REMOTE_ACTORS.ACTOR_URI)
            .from(REMOTE_NOTES).join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
            .where(REMOTE_NOTES.ID.eq(id)).fetchOne() ?: return@withConnection listOf()
        val followers = dsl.select(ACCOUNT_FOLLOWING.ACCOUNT_ID).from(ACCOUNT_FOLLOWING)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(ACCOUNT_FOLLOWING.ACCOUNT_ID))
            .where(ACCOUNT_FOLLOWING.ACTOR_URI.eq(note.get(REMOTE_ACTORS.ACTOR_URI)))
            .and(ACCOUNT_FOLLOWING.ACCOUNT_ID.gt(after?.value ?: 0L))
            .and(ACCOUNTS.LAST_ACTIVE_AT.ge(StoredInstant.format(activeSince)))
            .and(
                when (note.get(REMOTE_NOTES.VISIBILITY)) {
                    "public", "unlisted" -> DSL.trueCondition()

                    "followers" -> ACCOUNT_FOLLOWING.ACCEPTED_AT.isNotNull.and(
                        DSL.exists(
                            dsl.selectOne().from(REMOTE_NOTE_RECIPIENTS).where(REMOTE_NOTE_RECIPIENTS.REMOTE_NOTE_ID.eq(id))
                                .and(REMOTE_NOTE_RECIPIENTS.ACCOUNT_ID.eq(ACCOUNT_FOLLOWING.ACCOUNT_ID)).and(REMOTE_NOTE_RECIPIENTS.KIND.eq("followers")),
                        ),
                    )

                    else -> DSL.falseCondition()
                },
            )
        val addressed = dsl.select(REMOTE_NOTE_RECIPIENTS.ACCOUNT_ID).from(REMOTE_NOTE_RECIPIENTS)
            .where(REMOTE_NOTE_RECIPIENTS.REMOTE_NOTE_ID.eq(id)).and(REMOTE_NOTE_RECIPIENTS.KIND.eq("explicit"))
            .and(REMOTE_NOTE_RECIPIENTS.ACCOUNT_ID.gt(after?.value ?: 0L))
        followers.union(addressed).orderBy(1).limit(limit).fetch { AccountId(checkNotNull(it.value1())) }
    }

    override fun touch(accountId: AccountId, now: Instant, updateIntervalSeconds: Long) {
        jooq.withConnection { dsl ->
            dsl.update(ACCOUNTS).set(ACCOUNTS.LAST_ACTIVE_AT, StoredInstant.format(now)).where(ACCOUNTS.ID.eq(accountId.value))
                .and(ACCOUNTS.LAST_ACTIVE_AT.isNull.or(ACCOUNTS.LAST_ACTIVE_AT.le(StoredInstant.format(now.minusSeconds(updateIntervalSeconds)))))
                .execute()
        }
    }

    override fun rebuild(accountId: AccountId, limit: Int): List<TimelinePosition> = positions(accountId, null, 20, limit)

    override fun page(accountId: AccountId, after: TimelinePosition?, limit: Int): List<TimelinePosition> = positions(accountId, after, limit, limit)

    override fun hydrate(accountId: AccountId, members: List<String>): List<TimelinePost> {
        if (members.isEmpty()) return listOf()
        val localIds = members.filter { it.startsWith("l") }.map { it.drop(1).toLong() }
        val remoteIds = members.filter { it.startsWith("r") }.map { it.drop(1).toLong() }
        return jooq.withConnection { dsl ->
            val local = dsl.select(
                NOTES.ID, NOTES.CONTENT_HTML, NOTES.PUBLISHED_AT, ACCOUNTS.USERNAME, ACCOUNTS.DISPLAY_NAME,
                DSL.inline(null, String::class.java), DSL.inline(null, String::class.java), DSL.inline(0L), DSL.inline(null, Long::class.java),
                DSL.inline(null, String::class.java), DSL.inline(null, String::class.java),
                dsl.favouriteCountOf(NOTES.ID),
                NOTES.PUBLIC_ID,
            ).from(NOTES).join(ACCOUNTS).on(ACCOUNTS.ID.eq(NOTES.ACCOUNT_ID))
                .where(NOTES.ID.`in`(localIds)).and(NOTES.ACCOUNT_ID.eq(accountId.value))
            val remote = dsl.select(
                REMOTE_NOTES.ID, REMOTE_NOTES.CONTENT_HTML, REMOTE_NOTES.PUBLISHED_AT,
                DSL.coalesce(REMOTE_ACTORS.PREFERRED_USERNAME, REMOTE_ACTORS.ACTOR_URI), REMOTE_ACTORS.DISPLAY_NAME,
                REMOTE_ACTORS.ACTOR_URI, REMOTE_NOTES.SUMMARY, REMOTE_NOTES.SENSITIVE, REMOTE_NOTES.SCORE_MS,
                REMOTE_ACTORS.ICON_URL, REMOTE_ACTORS.UPDATED_AT, DSL.inline(null, Long::class.java),
                REMOTE_NOTES.OBJECT_URI,
            ).from(REMOTE_NOTES).join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
                .where(REMOTE_NOTES.ID.`in`(remoteIds)).and(dsl.remoteNoteReadableBy(accountId))
            val imagesByMember = remoteImages(dsl, remoteIds) + localImages(dsl, localIds)
            val likedRemoteIds = dsl.select(REMOTE_NOTE_LIKES.REMOTE_NOTE_ID).from(REMOTE_NOTE_LIKES)
                .where(REMOTE_NOTE_LIKES.ACCOUNT_ID.eq(accountId.value)).and(REMOTE_NOTE_LIKES.REMOTE_NOTE_ID.`in`(remoteIds))
                .fetchSet(REMOTE_NOTE_LIKES.REMOTE_NOTE_ID)
            val likedLocalIds = dsl.select(NOTE_LIKES.NOTE_ID).from(NOTE_LIKES)
                .where(NOTE_LIKES.ACCOUNT_ID.eq(accountId.value)).and(NOTE_LIKES.NOTE_ID.`in`(localIds))
                .fetchSet(NOTE_LIKES.NOTE_ID)
            val stampsByMember = if (localIds.isEmpty()) mapOf() else dsl.stampCountsBy(NOTES.ID, NOTES.ID.`in`(localIds)).mapKeys { "l${it.key}" }
            local.unionAll(remote).fetch { record ->
                val published = StoredInstant.parse(checkNotNull(record.value3()))
                val actor = record.value6()
                val member = "${if (actor == null) "l" else "r"}${record.value1()}"
                val noteKey = checkNotNull(record.value13())
                TimelinePost(
                    TimelinePosition(record.value9() ?: published.toEpochMilli(), member),
                    checkNotNull(record.value2()),
                    published,
                    actor,
                    checkNotNull(record.value4()),
                    record.value5(),
                    record.value7(),
                    record.value8() == 1L,
                    record.value10(),
                    record.value11()?.let { StoredInstant.parse(it) },
                    imagesByMember[member].orEmpty(),
                    record.value12(),
                    stampsByMember[member].orEmpty(),
                    record.value1() in if (actor == null) likedLocalIds else likedRemoteIds,
                    if (actor == null) TimelineNoteRef.Local(PublicNoteId(noteKey)) else TimelineNoteRef.Remote(noteKey),
                )
            }
        }
    }

    override fun actorPosts(accountId: AccountId, actorUri: String, limit: Int): List<TimelinePost> {
        val members = jooq.withConnection { dsl ->
            dsl.select(REMOTE_NOTES.ID, REMOTE_NOTES.SCORE_MS).from(REMOTE_NOTES)
                .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
                .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri)).and(dsl.remoteNoteReadableBy(accountId))
                .orderBy(REMOTE_NOTES.SCORE_MS.desc(), REMOTE_NOTES.ID.desc()).limit(limit)
                .fetch { it.toRemotePosition().member }
        }
        val hydrated = hydrate(accountId, members).associateBy { it.position.member }
        return members.mapNotNull { hydrated[it] }
    }

    override fun localPosition(publicId: String): TimelinePosition? = jooq.withConnection { dsl ->
        dsl.select(NOTES.ID, NOTES.PUBLISHED_AT).from(NOTES).where(NOTES.PUBLIC_ID.eq(publicId)).fetchOne()?.toLocalPosition()
    }

    private fun positions(accountId: AccountId, after: TimelinePosition?, perActorLimit: Int, limit: Int): List<TimelinePosition> = jooq.withConnection { dsl ->
        val actorIds = dsl.select(REMOTE_ACTORS.ID).from(ACCOUNT_FOLLOWING)
            .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ACTOR_URI.eq(ACCOUNT_FOLLOWING.ACTOR_URI))
            .where(ACCOUNT_FOLLOWING.ACCOUNT_ID.eq(accountId.value)).fetch(REMOTE_ACTORS.ID)
        val perActor = actorIds.map { actorId ->
            dsl.select(REMOTE_NOTES.ID, REMOTE_NOTES.SCORE_MS).from(REMOTE_NOTES)
                .where(REMOTE_NOTES.REMOTE_ACTOR_ID.eq(actorId)).and(dsl.remoteNoteReadableBy(accountId))
                .and(olderThan(REMOTE_NOTES.SCORE_MS, remoteMember, after))
                .orderBy(REMOTE_NOTES.SCORE_MS.desc(), remoteMember.desc()).limit(perActorLimit).fetch { it.toRemotePosition() }
        }
        val newestFirst = compareByDescending<TimelinePosition> { it.score }.thenByDescending { it.member }
        val cutoff = if (perActorLimit < limit) {
            perActor.filter { it.size == perActorLimit }.map { it.last() }.minWithOrNull(newestFirst)
        } else {
            null
        }
        val addressed = dsl.select(REMOTE_NOTE_RECIPIENTS.SCORE_MS, REMOTE_NOTE_RECIPIENTS.MEMBER).from(REMOTE_NOTE_RECIPIENTS)
            .where(REMOTE_NOTE_RECIPIENTS.ACCOUNT_ID.eq(accountId.value)).and(REMOTE_NOTE_RECIPIENTS.KIND.eq("explicit"))
            .and(olderThan(REMOTE_NOTE_RECIPIENTS.SCORE_MS, REMOTE_NOTE_RECIPIENTS.MEMBER, after))
            .orderBy(REMOTE_NOTE_RECIPIENTS.SCORE_MS.desc(), REMOTE_NOTE_RECIPIENTS.MEMBER.desc()).limit(limit)
            .fetch { TimelinePosition(checkNotNull(it.value1()), checkNotNull(it.value2())) }
        (perActor.flatten() + addressed + localPositions(dsl, accountId, after, limit)).distinctBy { it.member }
            .filter { cutoff == null || newestFirst.compare(it, cutoff) <= 0 }
            .sortedWith(newestFirst).take(limit)
    }

    private fun localPositions(dsl: DSLContext, accountId: AccountId, after: TimelinePosition?, limit: Int): List<TimelinePosition> {
        val cursor = if (after == null) {
            DSL.noCondition()
        } else {
            val floor = StoredInstant.format(Instant.ofEpochMilli(after.score))
            val upper = StoredInstant.format(Instant.ofEpochMilli(after.score).plusMillis(1))
            NOTES.PUBLISHED_AT.lt(upper).and(NOTES.PUBLISHED_AT.lt(floor).or(localMember.lt(after.member)))
        }
        val recent = dsl.select(NOTES.ID, NOTES.PUBLISHED_AT).from(NOTES).where(NOTES.ACCOUNT_ID.eq(accountId.value)).and(cursor)
            .orderBy(NOTES.PUBLISHED_AT.desc()).limit(limit).fetch { it.toLocalPosition() }
        val boundary = recent.lastOrNull()?.score ?: return listOf()
        val tied = dsl.select(NOTES.ID, NOTES.PUBLISHED_AT).from(NOTES).where(NOTES.ACCOUNT_ID.eq(accountId.value)).and(cursor)
            .and(NOTES.PUBLISHED_AT.ge(StoredInstant.format(Instant.ofEpochMilli(boundary))))
            .and(NOTES.PUBLISHED_AT.lt(StoredInstant.format(Instant.ofEpochMilli(boundary).plusMillis(1))))
            .orderBy(localMember.desc()).limit(limit).fetch { it.toLocalPosition() }
        return recent.filter { it.score > boundary } + tied
    }

    private fun remoteImages(dsl: DSLContext, remoteIds: List<Long>): Map<String, List<TimelinePostImage>> {
        if (remoteIds.isEmpty()) return mapOf()
        return dsl.select(REMOTE_NOTE_IMAGES.REMOTE_NOTE_ID, REMOTE_NOTE_IMAGES.URL, REMOTE_NOTE_IMAGES.DESCRIPTION)
            .from(REMOTE_NOTE_IMAGES).where(REMOTE_NOTE_IMAGES.REMOTE_NOTE_ID.`in`(remoteIds))
            .orderBy(REMOTE_NOTE_IMAGES.REMOTE_NOTE_ID, REMOTE_NOTE_IMAGES.POSITION)
            .fetch()
            .groupBy(
                keySelector = { "r${it.value1()}" },
                valueTransform = { TimelinePostImage.Remote(url = checkNotNull(it.value2()), description = it.value3()) },
            )
    }

    private fun localImages(dsl: DSLContext, localIds: List<Long>): Map<String, List<TimelinePostImage>> {
        if (localIds.isEmpty()) return mapOf()
        return dsl.select(NOTE_IMAGES.NOTE_ID, NOTE_IMAGES.PUBLIC_ID, NOTE_IMAGES.DESCRIPTION)
            .from(NOTE_IMAGES)
            .where(NOTE_IMAGES.NOTE_ID.`in`(localIds))
            .orderBy(NOTE_IMAGES.NOTE_ID, NOTE_IMAGES.POSITION)
            .fetch()
            .groupBy(
                keySelector = { "l${it.value1()}" },
                valueTransform = { TimelinePostImage.Local(publicId = NoteImagePublicId(checkNotNull(it.value2())), description = it.value3()) },
            )
    }

    private fun olderThan(score: Field<Long?>, member: Field<String?>, after: TimelinePosition?): Condition =
        if (after == null) DSL.noCondition() else score.le(after.score).and(score.lt(after.score).or(member.lt(after.member)))

    private fun addRecipient(dsl: DSLContext, noteId: Long, accountId: Long, kind: String, score: Long) {
        dsl.insertInto(REMOTE_NOTE_RECIPIENTS).set(REMOTE_NOTE_RECIPIENTS.REMOTE_NOTE_ID, noteId)
            .set(
                REMOTE_NOTE_RECIPIENTS.ACCOUNT_ID,
                accountId,
            ).set(REMOTE_NOTE_RECIPIENTS.KIND, kind).set(REMOTE_NOTE_RECIPIENTS.SCORE_MS, score).set(REMOTE_NOTE_RECIPIENTS.MEMBER, "r$noteId").execute()
    }

    private fun Record.toLocalPosition(): TimelinePosition = TimelinePosition(
        StoredInstant.parse(checkNotNull(get(NOTES.PUBLISHED_AT))).toEpochMilli(),
        "l${get(NOTES.ID)}",
    )

    private fun Record.toRemotePosition(): TimelinePosition = TimelinePosition(checkNotNull(get(REMOTE_NOTES.SCORE_MS)), "r${get(REMOTE_NOTES.ID)}")
}
