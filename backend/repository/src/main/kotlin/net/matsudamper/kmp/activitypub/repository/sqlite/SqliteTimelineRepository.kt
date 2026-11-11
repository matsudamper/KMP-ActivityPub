package net.matsudamper.kmp.activitypub.repository.sqlite

import java.net.URI
import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.RemoteBoostInput
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kmp.activitypub.repository.RemoteNoteReceptionInterest
import net.matsudamper.kmp.activitypub.repository.TimelineBooster
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.records.AccountBoostsRecord
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_BOOSTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_FOLLOWING
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_BOOSTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_IMAGES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_LIKES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_BOOSTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTE_EMOJIS
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
import org.jooq.TableField
import org.jooq.impl.DSL

internal class SqliteTimelineRepository(private val jooq: SqliteJooq) : TimelineRepository {
    private val remoteMember = DSL.inline("r").concat(REMOTE_NOTES.ID.cast(String::class.java))
    private val localMember = DSL.inline("l").concat(NOTES.ID.cast(String::class.java))
    private val boostMember = DSL.inline("b").concat(REMOTE_BOOSTS.ID.cast(String::class.java))
    private val accountBoostMember = DSL.inline("a").concat(ACCOUNT_BOOSTS.ID.cast(String::class.java))

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
        val id = insertRemoteNote(dsl, note, actorId, minOf(note.publishedAt, note.receivedAt).toEpochMilli(), viaBoost = false)
            ?: takeOverBoostedNote(dsl, note.objectUri)
            ?: return@transaction null
        // ブーストで先に保存していた投稿は、そのときの位置に揃える
        val score = checkNotNull(dsl.select(REMOTE_NOTES.SCORE_MS).from(REMOTE_NOTES).where(REMOTE_NOTES.ID.eq(id)).fetchOne(REMOTE_NOTES.SCORE_MS))
        addressed.forEach { accountId -> addRecipient(dsl, id, checkNotNull(accountId), "explicit", score) }
        if (note.visibility == "followers") {
            followers.filter { it.get(ACCOUNT_FOLLOWING.ACCEPTED_AT) != null }.forEach { follower ->
                addRecipient(dsl, id, checkNotNull(follower.get(ACCOUNT_FOLLOWING.ACCOUNT_ID)), "followers", score)
            }
        }
        TimelinePosition(score, "r$id")
    }

    override fun receiveBoost(boost: RemoteBoostInput): TimelinePosition? = jooq.transaction { dsl ->
        val boosterId = dsl.select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(boost.boosterUri))
            .fetchOne(REMOTE_ACTORS.ID) ?: return@transaction null
        if (!dsl.fetchExists(ACCOUNT_FOLLOWING, ACCOUNT_FOLLOWING.ACTOR_URI.eq(boost.boosterUri))) return@transaction null
        val note = boost.note
        // ブーストの相手のフォロワーには投稿者をフォローしていない人も居るので、宛先を限った投稿を載せない
        if (note.visibility !in PUBLISHED_REMOTE_NOTE_VISIBILITIES) return@transaction null
        val noteId = existingPublishedNoteId(dsl, note.objectUri) ?: insertBoostedNote(dsl, note) ?: return@transaction null
        val score = minOf(boost.boostedAt, boost.receivedAt).toEpochMilli()
        val inserted = dsl.insertInto(REMOTE_BOOSTS)
            .set(REMOTE_BOOSTS.ACTIVITY_URI, boost.activityUri)
            .set(REMOTE_BOOSTS.REMOTE_ACTOR_ID, boosterId)
            .set(REMOTE_BOOSTS.REMOTE_NOTE_ID, noteId)
            .set(REMOTE_BOOSTS.SCORE_MS, score)
            .onConflictDoNothing()
            .execute()
        if (inserted == 0) return@transaction null
        val id = checkNotNull(
            dsl.select(REMOTE_BOOSTS.ID).from(REMOTE_BOOSTS).where(REMOTE_BOOSTS.ACTIVITY_URI.eq(boost.activityUri)).fetchOne(REMOTE_BOOSTS.ID),
        )
        TimelinePosition(score, "b$id")
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
        if (member.startsWith("a")) {
            // 自分が付けたブーストは、ブーストしたアカウントのタイムラインにだけ載る
            return@withConnection dsl.select(ACCOUNT_BOOSTS.ACCOUNT_ID).from(ACCOUNT_BOOSTS).where(ACCOUNT_BOOSTS.ID.eq(id))
                .and(ACCOUNT_BOOSTS.ACCOUNT_ID.gt(after?.value ?: 0L)).limit(limit)
                .fetch { AccountId(checkNotNull(it.value1())) }
        }
        if (member.startsWith("b")) {
            return@withConnection dsl.select(ACCOUNT_FOLLOWING.ACCOUNT_ID).from(REMOTE_BOOSTS)
                .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_BOOSTS.REMOTE_ACTOR_ID))
                .join(ACCOUNT_FOLLOWING).on(ACCOUNT_FOLLOWING.ACTOR_URI.eq(REMOTE_ACTORS.ACTOR_URI))
                .join(ACCOUNTS).on(ACCOUNTS.ID.eq(ACCOUNT_FOLLOWING.ACCOUNT_ID))
                .where(REMOTE_BOOSTS.ID.eq(id))
                .and(ACCOUNT_FOLLOWING.ACCOUNT_ID.gt(after?.value ?: 0L))
                .and(ACCOUNTS.LAST_ACTIVE_AT.ge(StoredInstant.format(activeSince)))
                .orderBy(ACCOUNT_FOLLOWING.ACCOUNT_ID).limit(limit)
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
        val localIds = members.mapNotNull { TimelineMembers.localNoteIdOf(it) }
        val remoteIds = members.mapNotNull { TimelineMembers.remoteNoteIdOf(it) }
        val boostIds = members.mapNotNull { TimelineMembers.boostIdOf(it) }
        val accountBoostIds = members.mapNotNull { TimelineMembers.accountBoostIdOf(it) }
        return jooq.withConnection { dsl ->
            val notes = hydrateNotes(
                dsl = dsl,
                viewer = accountId,
                localIds = localIds,
                remoteIds = remoteIds,
                // このサーバーの投稿でタイムラインに載るのは自分の投稿だけ
                localCondition = NOTES.ACCOUNT_ID.eq(accountId.value),
                remoteCondition = dsl.remoteNoteReadableBy(accountId),
            )
            boostedPosts(dsl, accountId, boostIds) +
                accountBoostedPosts(dsl, accountId, ACCOUNT_BOOSTS.ID.`in`(accountBoostIds).and(ACCOUNT_BOOSTS.ACCOUNT_ID.eq(accountId.value))) +
                notes
        }
    }

    /**
     * このサーバーの投稿と他のサーバーの投稿を、member の位置のまま組み立てる
     *
     * @param viewer いいねやブーストの有無を見るアカウント。ログインしていなければ null で、どれも付けていない扱いになる
     * @param localCondition 返すこのサーバーの投稿を絞る条件
     * @param remoteCondition 返す他のサーバーの投稿を絞る条件
     */
    private fun hydrateNotes(
        dsl: DSLContext,
        viewer: AccountId?,
        localIds: List<Long>,
        remoteIds: List<Long>,
        localCondition: Condition,
        remoteCondition: Condition,
    ): List<TimelinePost> {
        if (localIds.isEmpty() && remoteIds.isEmpty()) return listOf()
        val local = dsl.select(
            NOTES.ID, NOTES.CONTENT_HTML, NOTES.PUBLISHED_AT, ACCOUNTS.USERNAME, ACCOUNTS.DISPLAY_NAME,
            DSL.inline(null, String::class.java), DSL.inline(null, String::class.java), DSL.inline(0L), DSL.inline(null, Long::class.java),
            DSL.inline(null, String::class.java), DSL.inline(null, String::class.java),
            dsl.favouriteCountOf(NOTES.ID),
            DSL.field(dsl.selectCount().from(NOTE_BOOSTS).where(NOTE_BOOSTS.NOTE_ID.eq(NOTES.ID))).cast(Long::class.java)
                .plus(DSL.field(dsl.selectCount().from(ACCOUNT_BOOSTS).where(ACCOUNT_BOOSTS.NOTE_ID.eq(NOTES.ID))).cast(Long::class.java)),
            NOTES.PUBLIC_ID,
            DSL.inline(null, String::class.java),
        ).from(NOTES).join(ACCOUNTS).on(ACCOUNTS.ID.eq(NOTES.ACCOUNT_ID))
            .where(NOTES.ID.`in`(localIds)).and(localCondition)
        val remote = dsl.select(
            REMOTE_NOTES.ID, REMOTE_NOTES.CONTENT_HTML, REMOTE_NOTES.PUBLISHED_AT,
            DSL.coalesce(REMOTE_ACTORS.PREFERRED_USERNAME, REMOTE_ACTORS.ACTOR_URI), REMOTE_ACTORS.DISPLAY_NAME,
            REMOTE_ACTORS.ACTOR_URI, REMOTE_NOTES.SUMMARY, REMOTE_NOTES.SENSITIVE, REMOTE_NOTES.SCORE_MS,
            REMOTE_ACTORS.ICON_URL, REMOTE_ACTORS.UPDATED_AT, DSL.inline(null, Long::class.java), DSL.inline(null, Long::class.java),
            REMOTE_NOTES.OBJECT_URI,
            REMOTE_NOTES.VISIBILITY,
        ).from(REMOTE_NOTES).join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
            .where(REMOTE_NOTES.ID.`in`(remoteIds)).and(remoteCondition)
        val imagesByMember = remoteImages(dsl, remoteIds) + localImages(dsl, localIds)
        val emojisByNote = dsl.remoteNoteEmojisById(remoteIds)
        val likedRemoteIds = dsl.select(REMOTE_NOTE_LIKES.REMOTE_NOTE_ID).from(REMOTE_NOTE_LIKES)
            .where(REMOTE_NOTE_LIKES.ACCOUNT_ID.eqViewer(viewer)).and(REMOTE_NOTE_LIKES.REMOTE_NOTE_ID.`in`(remoteIds))
            .fetchSet(REMOTE_NOTE_LIKES.REMOTE_NOTE_ID)
        val likedLocalIds = dsl.select(NOTE_LIKES.NOTE_ID).from(NOTE_LIKES)
            .where(NOTE_LIKES.ACCOUNT_ID.eqViewer(viewer)).and(NOTE_LIKES.NOTE_ID.`in`(localIds))
            .fetchSet(NOTE_LIKES.NOTE_ID)
        val boostedRemoteIds = dsl.boostedNoteIds(viewer, ACCOUNT_BOOSTS.REMOTE_NOTE_ID, remoteIds)
        val boostedLocalIds = dsl.boostedNoteIds(viewer, ACCOUNT_BOOSTS.NOTE_ID, localIds)
        val stampsByMember = if (localIds.isEmpty()) mapOf() else dsl.stampCountsBy(NOTES.ID, NOTES.ID.`in`(localIds)).mapKeys { "l${it.key}" }
        val rows = local.unionAll(remote).fetch()
        val emojisByActor = dsl.remoteActorEmojisByUri(rows.mapNotNull { it.value6() }.toSet())
        return rows.map { record ->
            val published = StoredInstant.parse(checkNotNull(record.value3()))
            val actor = record.value6()
            val member = "${if (actor == null) "l" else "r"}${record.value1()}"
            val noteKey = checkNotNull(record.value14())
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
                if (actor == null) listOf() else emojisByNote[record.value1()].orEmpty(),
                if (actor == null) listOf() else emojisByActor[actor].orEmpty(),
                record.value12(),
                stampsByMember[member].orEmpty(),
                record.value1() in if (actor == null) likedLocalIds else likedRemoteIds,
                when {
                    actor == null -> record.value1() in boostedLocalIds
                    record.value15() == BOOSTABLE_REMOTE_NOTE_VISIBILITY -> record.value1() in boostedRemoteIds
                    else -> null
                },
                member,
                if (actor == null) TimelineNoteRef.Local(PublicNoteId(noteKey)) else TimelineNoteRef.Remote(noteKey),
                record.value13(),
                null,
            )
        }
    }

    /**
     * このサーバーのアカウントが付けたブースト。ブーストした時点で読めていたので、今読めるかは問わない
     *
     * @param viewer いいねやブーストの有無を見るアカウント
     * @param boostCondition 返す account_boosts を絞る条件
     */
    private fun accountBoostedPosts(dsl: DSLContext, viewer: AccountId?, boostCondition: Condition): List<TimelinePost> {
        val rows = dsl.select(ACCOUNT_BOOSTS.ID, ACCOUNT_BOOSTS.SCORE_MS, ACCOUNT_BOOSTS.REMOTE_NOTE_ID, ACCOUNT_BOOSTS.NOTE_ID, ACCOUNTS.USERNAME, ACCOUNTS.DISPLAY_NAME)
            .from(ACCOUNT_BOOSTS).join(ACCOUNTS).on(ACCOUNTS.ID.eq(ACCOUNT_BOOSTS.ACCOUNT_ID))
            .where(boostCondition)
            .fetch()
        if (rows.isEmpty()) return listOf()
        val notes = hydrateNotes(
            dsl = dsl,
            viewer = viewer,
            localIds = rows.mapNotNull { it.value4() },
            remoteIds = rows.mapNotNull { it.value3() },
            localCondition = DSL.noCondition(),
            remoteCondition = DSL.noCondition(),
        ).associateBy { it.position.member }
        return rows.mapNotNull { record ->
            val noteMember = record.value3()?.let { "r$it" } ?: "l${record.value4()}"
            val note = notes[noteMember] ?: return@mapNotNull null
            note.copy(
                position = TimelinePosition(checkNotNull(record.value2()), TimelineMembers.accountBoostMember(checkNotNull(record.value1()))),
                boostedBy = TimelineBooster(
                    actorUri = null,
                    username = checkNotNull(record.value5()),
                    displayName = record.value6(),
                    iconUrl = null,
                    emojis = listOf(),
                    updatedAt = null,
                ),
            )
        }
    }

    override fun storeFetchedNotes(viewer: AccountId, notes: List<RemoteNoteInput>): Map<String, TimelinePost> = jooq.transaction { dsl ->
        val remoteIds = notes.filter { it.visibility in PUBLISHED_REMOTE_NOTE_VISIBILITIES && it.isOnAuthorsHost() }
            .mapNotNull { note -> refreshFetchedNote(dsl, note) ?: insertBoostedNote(dsl, note) }
        hydrateNotes(
            dsl = dsl,
            viewer = viewer,
            localIds = listOf(),
            remoteIds = remoteIds,
            localCondition = DSL.noCondition(),
            remoteCondition = REMOTE_NOTES.VISIBILITY.`in`(PUBLISHED_REMOTE_NOTE_VISIBILITIES),
        ).associateBy { (it.noteRef as TimelineNoteRef.Remote).objectUri }
    }

    override fun accountBoosts(booster: AccountId, viewer: AccountId?, limit: Int): List<TimelinePost> = jooq.withConnection { dsl ->
        val boostIds = dsl.select(ACCOUNT_BOOSTS.ID).from(ACCOUNT_BOOSTS)
            .where(ACCOUNT_BOOSTS.ACCOUNT_ID.eq(booster.value))
            .orderBy(ACCOUNT_BOOSTS.SCORE_MS.desc(), ACCOUNT_BOOSTS.ID.desc()).limit(limit)
            .fetch(ACCOUNT_BOOSTS.ID)
        accountBoostedPosts(dsl, viewer, ACCOUNT_BOOSTS.ID.`in`(boostIds))
            .sortedWith(compareByDescending<TimelinePost> { it.position.score }.thenByDescending { TimelineMembers.accountBoostIdOf(it.position.member) })
    }

    override fun actorPosts(accountId: AccountId, actorUri: String, limit: Int): List<TimelinePost> {
        val members = jooq.withConnection { dsl ->
            val notes = dsl.select(REMOTE_NOTES.ID, REMOTE_NOTES.SCORE_MS).from(REMOTE_NOTES)
                .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
                .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri)).and(dsl.remoteNoteReadableBy(accountId))
                .orderBy(REMOTE_NOTES.SCORE_MS.desc(), REMOTE_NOTES.ID.desc()).limit(limit)
                .fetch { it.toRemotePosition() }
            val boosts = dsl.select(REMOTE_BOOSTS.ID, REMOTE_BOOSTS.SCORE_MS).from(REMOTE_BOOSTS)
                .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_BOOSTS.REMOTE_ACTOR_ID))
                .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
                .orderBy(REMOTE_BOOSTS.SCORE_MS.desc(), boostMember.desc()).limit(limit)
                .fetch { TimelinePosition(checkNotNull(it.value2()), "b${it.value1()}") }
            (notes + boosts).sortedWith(compareByDescending<TimelinePosition> { it.score }.thenByDescending { it.member })
                .take(limit).map { it.member }
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
        val perActor = actorIds.flatMap { actorId ->
            val notes = dsl.select(REMOTE_NOTES.ID, REMOTE_NOTES.SCORE_MS).from(REMOTE_NOTES)
                .where(REMOTE_NOTES.REMOTE_ACTOR_ID.eq(actorId)).and(dsl.remoteNoteReadableBy(accountId))
                .and(olderThan(REMOTE_NOTES.SCORE_MS, remoteMember, after))
                .orderBy(REMOTE_NOTES.SCORE_MS.desc(), remoteMember.desc()).limit(perActorLimit).fetch { it.toRemotePosition() }
            val boosts = dsl.select(REMOTE_BOOSTS.ID, REMOTE_BOOSTS.SCORE_MS).from(REMOTE_BOOSTS)
                .where(REMOTE_BOOSTS.REMOTE_ACTOR_ID.eq(actorId))
                .and(olderThan(REMOTE_BOOSTS.SCORE_MS, boostMember, after))
                .orderBy(REMOTE_BOOSTS.SCORE_MS.desc(), boostMember.desc()).limit(perActorLimit)
                .fetch { TimelinePosition(checkNotNull(it.value2()), "b${it.value1()}") }
            listOf(notes, boosts)
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
        val ownBoosts = dsl.select(ACCOUNT_BOOSTS.ID, ACCOUNT_BOOSTS.SCORE_MS).from(ACCOUNT_BOOSTS)
            .where(ACCOUNT_BOOSTS.ACCOUNT_ID.eq(accountId.value))
            .and(olderThan(ACCOUNT_BOOSTS.SCORE_MS, accountBoostMember, after))
            .orderBy(ACCOUNT_BOOSTS.SCORE_MS.desc(), accountBoostMember.desc()).limit(limit)
            .fetch { TimelinePosition(checkNotNull(it.value2()), TimelineMembers.accountBoostMember(checkNotNull(it.value1()))) }
        (perActor.flatten() + addressed + ownBoosts + localPositions(dsl, accountId, after, limit)).distinctBy { it.member }
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

    /**
     * @return 入れた投稿の id。同じ投稿が既にあれば null
     */
    private fun insertRemoteNote(dsl: DSLContext, note: RemoteNoteInput, actorId: Long, score: Long, viaBoost: Boolean): Long? {
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
            .set(REMOTE_NOTES.VIA_BOOST, if (viaBoost) 1L else 0L)
            .onConflict(REMOTE_NOTES.OBJECT_URI).doNothing()
            .execute()
        if (inserted == 0) return null
        val id = checkNotNull(dsl.select(REMOTE_NOTES.ID).from(REMOTE_NOTES).where(REMOTE_NOTES.OBJECT_URI.eq(note.objectUri)).fetchOne(REMOTE_NOTES.ID))
        insertRemoteNoteImages(dsl, id, note.images)
        dsl.insertRemoteNoteEmojis(id, note.emojis)
        return id
    }

    private fun insertRemoteNoteImages(dsl: DSLContext, remoteNoteId: Long, images: List<TimelinePostImage.Remote>) {
        images.forEachIndexed { index, image ->
            dsl.insertInto(REMOTE_NOTE_IMAGES)
                .set(REMOTE_NOTE_IMAGES.REMOTE_NOTE_ID, remoteNoteId)
                .set(REMOTE_NOTE_IMAGES.POSITION, index.toLong())
                .set(REMOTE_NOTE_IMAGES.URL, image.url)
                .set(REMOTE_NOTE_IMAGES.DESCRIPTION, image.description)
                .execute()
        }
    }

    /**
     * 保存済みの公開している投稿を、相手のサーバーから取り直した内容に書き換える。保存した後に編集されていることがある。
     * 公開範囲も取り直した値にする。どちらも公開している範囲の中での変更に限る。位置は変えない。[RemoteNoteInput.actorUri] の投稿でなければ書き換えない
     *
     * @return 書き換えた投稿の id。保存していなければ null
     */
    private fun refreshFetchedNote(dsl: DSLContext, note: RemoteNoteInput): Long? {
        val id = dsl.select(REMOTE_NOTES.ID).from(REMOTE_NOTES)
            .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
            .where(REMOTE_NOTES.OBJECT_URI.eq(note.objectUri))
            .and(REMOTE_ACTORS.ACTOR_URI.eq(note.actorUri))
            .and(REMOTE_NOTES.VISIBILITY.`in`(PUBLISHED_REMOTE_NOTE_VISIBILITIES))
            .fetchOne(REMOTE_NOTES.ID)
            ?: return null
        dsl.update(REMOTE_NOTES)
            .set(REMOTE_NOTES.VISIBILITY, note.visibility)
            .set(REMOTE_NOTES.CONTENT_HTML, note.contentHtml)
            .set(REMOTE_NOTES.SUMMARY, note.summary)
            .set(REMOTE_NOTES.SENSITIVE, if (note.sensitive) 1L else 0L)
            .where(REMOTE_NOTES.ID.eq(id))
            .execute()
        dsl.deleteFrom(REMOTE_NOTE_IMAGES).where(REMOTE_NOTE_IMAGES.REMOTE_NOTE_ID.eq(id)).execute()
        insertRemoteNoteImages(dsl, id, note.images)
        dsl.deleteFrom(REMOTE_NOTE_EMOJIS).where(REMOTE_NOTE_EMOJIS.REMOTE_NOTE_ID.eq(id)).execute()
        dsl.insertRemoteNoteEmojis(id, note.emojis)
        return id
    }

    /**
     * @return 入れた投稿の id。投稿者が記録に無ければ null
     */
    private fun insertBoostedNote(dsl: DSLContext, note: RemoteNoteInput): Long? {
        val authorId = dsl.select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(note.actorUri))
            .fetchOne(REMOTE_ACTORS.ID) ?: return null
        return insertRemoteNote(dsl, note, authorId, minOf(note.publishedAt, note.receivedAt).toEpochMilli(), viaBoost = true)
    }

    /**
     * ブーストで先に保存した投稿に Create が届いたら、通常の受信として宛先を登録できるようにする
     *
     * @return ブーストで保存しただけの投稿だったらその id。通常の受信で保存済みなら null
     */
    private fun takeOverBoostedNote(dsl: DSLContext, objectUri: String): Long? {
        val updated = dsl.update(REMOTE_NOTES).set(REMOTE_NOTES.VIA_BOOST, 0L)
            .where(REMOTE_NOTES.OBJECT_URI.eq(objectUri)).and(REMOTE_NOTES.VIA_BOOST.eq(1L))
            .execute()
        if (updated == 0) return null
        return dsl.select(REMOTE_NOTES.ID).from(REMOTE_NOTES).where(REMOTE_NOTES.OBJECT_URI.eq(objectUri)).fetchOne(REMOTE_NOTES.ID)
    }

    private fun existingPublishedNoteId(dsl: DSLContext, objectUri: String): Long? =
        dsl.select(REMOTE_NOTES.ID).from(REMOTE_NOTES)
            .where(REMOTE_NOTES.OBJECT_URI.eq(objectUri)).and(REMOTE_NOTES.VISIBILITY.`in`(PUBLISHED_REMOTE_NOTE_VISIBILITIES))
            .fetchOne(REMOTE_NOTES.ID)

    /**
     * ブーストした相手をフォローしているアカウントにだけ見せる。ブーストされた投稿は公開か、公開タイムラインに載せない公開のものしか記録しないので、投稿者のフォローは問わない
     */
    private fun boostedPosts(dsl: DSLContext, accountId: AccountId, boostIds: List<Long>): List<TimelinePost> {
        if (boostIds.isEmpty()) return listOf()
        val booster = REMOTE_ACTORS.`as`("booster")
        val rows = dsl.select(
            REMOTE_BOOSTS.ID, REMOTE_BOOSTS.SCORE_MS, REMOTE_NOTES.ID, REMOTE_NOTES.CONTENT_HTML, REMOTE_NOTES.PUBLISHED_AT,
            REMOTE_NOTES.SUMMARY, REMOTE_NOTES.SENSITIVE,
            REMOTE_ACTORS.ACTOR_URI, REMOTE_ACTORS.PREFERRED_USERNAME, REMOTE_ACTORS.DISPLAY_NAME, REMOTE_ACTORS.ICON_URL, REMOTE_ACTORS.UPDATED_AT,
            booster.ACTOR_URI, booster.PREFERRED_USERNAME, booster.DISPLAY_NAME, booster.ICON_URL, booster.UPDATED_AT,
            REMOTE_NOTES.OBJECT_URI, REMOTE_NOTES.VISIBILITY,
        ).from(REMOTE_BOOSTS)
            .join(REMOTE_NOTES).on(REMOTE_NOTES.ID.eq(REMOTE_BOOSTS.REMOTE_NOTE_ID))
            .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
            .join(booster).on(booster.ID.eq(REMOTE_BOOSTS.REMOTE_ACTOR_ID))
            .where(REMOTE_BOOSTS.ID.`in`(boostIds))
            .and(REMOTE_NOTES.VISIBILITY.`in`(PUBLISHED_REMOTE_NOTE_VISIBILITIES))
            .andExists(
                dsl.selectOne().from(ACCOUNT_FOLLOWING)
                    .where(ACCOUNT_FOLLOWING.ACCOUNT_ID.eq(accountId.value)).and(ACCOUNT_FOLLOWING.ACTOR_URI.eq(booster.ACTOR_URI)),
            )
            .fetch()
        val likedNoteIds = dsl.select(REMOTE_NOTE_LIKES.REMOTE_NOTE_ID).from(REMOTE_NOTE_LIKES)
            .where(REMOTE_NOTE_LIKES.ACCOUNT_ID.eq(accountId.value)).and(REMOTE_NOTE_LIKES.REMOTE_NOTE_ID.`in`(rows.map { checkNotNull(it.value3()) }))
            .fetchSet(REMOTE_NOTE_LIKES.REMOTE_NOTE_ID)
        val boostedNoteIds = dsl.boostedNoteIds(accountId, ACCOUNT_BOOSTS.REMOTE_NOTE_ID, rows.map { checkNotNull(it.value3()) })
        val imagesByNote = remoteImages(dsl, rows.map { checkNotNull(it.value3()) })
        val emojisByNote = dsl.remoteNoteEmojisById(rows.map { checkNotNull(it.value3()) })
        val emojisByActor = dsl.remoteActorEmojisByUri(rows.flatMap { listOf(checkNotNull(it.value8()), checkNotNull(it.value13())) }.toSet())
        return rows.map { record ->
            val authorUri = checkNotNull(record.value8())
            val boosterUri = checkNotNull(record.value13())
            TimelinePost(
                position = TimelinePosition(checkNotNull(record.value2()), "b${record.value1()}"),
                contentHtml = checkNotNull(record.value4()),
                publishedAt = StoredInstant.parse(checkNotNull(record.value5())),
                actorUri = authorUri,
                username = record.value9() ?: authorUri,
                displayName = record.value10(),
                summary = record.value6(),
                sensitive = record.value7() == 1L,
                actorIconUrl = record.value11(),
                actorUpdatedAt = record.value12()?.let { StoredInstant.parse(it) },
                images = imagesByNote["r${record.value3()}"].orEmpty(),
                emojis = emojisByNote[record.value3()].orEmpty(),
                actorEmojis = emojisByActor[authorUri].orEmpty(),
                favouriteCount = null,
                stamps = listOf(),
                likedByMe = record.value3() in likedNoteIds,
                boostedByMe = if (record.value19() == BOOSTABLE_REMOTE_NOTE_VISIBILITY) record.value3() in boostedNoteIds else null,
                reactionMember = "r${record.value3()}",
                noteRef = TimelineNoteRef.Remote(checkNotNull(record.value18())),
                boostCount = null,
                boostedBy = TimelineBooster(
                    actorUri = boosterUri,
                    username = record.value14() ?: boosterUri,
                    displayName = record.value15(),
                    iconUrl = record.value16(),
                    emojis = emojisByActor[boosterUri].orEmpty(),
                    updatedAt = StoredInstant.parse(checkNotNull(record.value17())),
                ),
            )
        }
    }

    private fun DSLContext.boostedNoteIds(viewer: AccountId?, noteIdField: TableField<AccountBoostsRecord, Long?>, noteIds: List<Long>): Set<Long?> =
        if (noteIds.isEmpty()) {
            setOf()
        } else {
            select(noteIdField).from(ACCOUNT_BOOSTS)
                .where(ACCOUNT_BOOSTS.ACCOUNT_ID.eqViewer(viewer)).and(noteIdField.`in`(noteIds))
                .fetchSet(noteIdField)
        }

    private fun Field<Long?>.eqViewer(viewer: AccountId?): Condition = if (viewer == null) DSL.falseCondition() else eq(viewer.value)

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

/**
 * 投稿の id が投稿者と同じホストにあるか。outbox の中に埋め込まれた投稿は取りに行かずに読むので、
 * 他のサーバーの投稿の id を名乗る投稿を保存すると、後で本物の投稿が届いたときに取り違える
 */
private fun RemoteNoteInput.isOnAuthorsHost(): Boolean {
    val noteHost = runCatching { URI(objectUri).host }.getOrNull() ?: return false
    val actorHost = runCatching { URI(actorUri).host }.getOrNull() ?: return false
    return noteHost.equals(actorHost, ignoreCase = true)
}
