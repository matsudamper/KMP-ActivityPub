package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.time.Instant
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.matsudamper.kmp.activitypub.repository.AccountBoostRepository
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.BoostRepository
import net.matsudamper.kmp.activitypub.repository.CustomEmojiRecord
import net.matsudamper.kmp.activitypub.repository.FavouriteRepository
import net.matsudamper.kmp.activitypub.repository.LikeRepository
import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.NotePinRepository
import net.matsudamper.kmp.activitypub.repository.NotePosition
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.ProfileLink
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kmp.activitypub.repository.StampCount
import net.matsudamper.kmp.activitypub.repository.StampRepository
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.slf4j.LoggerFactory

class UserProfileService(
    private val accounts: AccountRepository,
    private val remoteActors: RemoteActorRepository,
    private val notes: NoteRepository,
    private val favourites: FavouriteRepository,
    private val stamps: StampRepository,
    private val likes: LikeRepository,
    private val boosts: BoostRepository,
    private val accountBoosts: AccountBoostRepository,
    private val timeline: TimelineRepository,
    private val pins: NotePinRepository,
    private val following: FollowingService,
    private val remoteProfiles: RemoteUserProfiles?,
    private val localDomain: String,
) {
    private val logger = LoggerFactory.getLogger(UserProfileService::class.java)
    private val remoteFetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * プロフィールの情報だけを返す。投稿は相手のサーバーの outbox を待つことがあるので、[firstPosts] で別に読む
     */
    suspend fun profile(acct: String, viewer: AccountId?): UserProfile? {
        val target = ProfileAcct.parse(acct, localDomain) ?: return null
        if (target.isLocal) {
            val account = accounts.findByUsername(target.username) ?: return null
            val detail = accounts.findProfileDetail(account.username)
            return UserProfile(
                user = SearchedUser(null, account.username, localDomain, true, account.displayName ?: account.username, null, listOf(), null),
                following = false,
                headerUrl = null,
                biography = detail?.summary,
                links = detail?.links.orEmpty(),
                viewerIsOwner = account.id == viewer,
            )
        }
        val storedActor = remoteActors.findByAcct(target.username, target.host)
        val followed = following.isFollowing(viewer, storedActor?.actorUri)
        if (followed) {
            val actor = checkNotNull(storedActor)
            return UserProfile(
                user = SearchedUser(actor.actorUri, actor.preferredUsername, target.host, false, actor.displayName ?: actor.preferredUsername, actor.iconUrl, actor.emojis, actor.updatedAt),
                following = true,
                headerUrl = remoteActors.findByUri(actor.actorUri)?.profile?.headerUrl,
                biography = null,
                links = listOf(),
                viewerIsOwner = false,
            )
        }
        if (viewer == null) return null
        val profiles = remoteProfiles ?: return null
        val remote = if (storedActor != null) profiles.findByActorUri(storedActor.actorUri) else profiles.find(target.username, target.host)
        if (remote == null) return null
        val actorHost = URI(remote.user.actorUri).host ?: return null
        return UserProfile(
            user = SearchedUser(
                remote.user.actorUri,
                remote.user.username,
                actorHost,
                false,
                remote.user.displayName ?: remote.user.username,
                remote.user.iconUrl,
                remote.user.displayNameEmojis,
                null,
            ),
            following = following.isFollowing(viewer, remote.user.actorUri),
            headerUrl = remote.headerUrl,
            biography = remote.biography?.let { htmlToPlainText(it) },
            links = listOf(),
            viewerIsOwner = false,
        )
    }

    /**
     * プロフィールの投稿の最初のページ。続きは [morePosts] で読む。
     *
     * 他のサーバーのユーザーは、[profile] で引いたときに保存したアクターを使うので、普通は相手のサーバーにユーザーを引き直さない
     */
    suspend fun firstPosts(acct: String, viewer: AccountId?): ProfilePostsResult {
        val target = ProfileAcct.parse(acct, localDomain) ?: return ProfilePostsResult.NotFound
        if (target.isLocal) {
            val account = accounts.findByUsername(target.username) ?: return ProfilePostsResult.NotFound
            return ProfilePostsResult.Found(localProfilePosts(accountId = account.id, username = account.username, viewer = viewer, cursor = null))
        }
        if (viewer == null) return ProfilePostsResult.NotLoggedIn
        val actorUri = remoteActors.findByAcct(target.username, target.host)?.actorUri
            ?: remoteProfiles?.find(target.username, target.host)?.user?.actorUri
            ?: return ProfilePostsResult.NotFound
        val followed = following.isFollowing(viewer, actorUri)
        return ProfilePostsResult.Found(firstRemotePostsPage(viewer = viewer, actorUri = actorUri, followed = followed))
    }

    /**
     * プロフィールの投稿の、[ProfilePostPage.nextCursor] から先
     */
    suspend fun morePosts(viewer: AccountId?, cursor: ProfilePostCursor): ProfilePostsResult = when (cursor) {
        is ProfilePostCursor.Local -> {
            val account = accounts.findByUsername(cursor.username)
            if (account == null) {
                ProfilePostsResult.NotFound
            } else {
                ProfilePostsResult.Found(localProfilePosts(accountId = account.id, username = account.username, viewer = viewer, cursor = cursor))
            }
        }

        is ProfilePostCursor.Remote -> if (viewer == null) {
            ProfilePostsResult.NotLoggedIn
        } else {
            val outboxPage = outboxPage(actorUri = cursor.actorUri, from = cursor.outbox)
            if (outboxPage == null) {
                ProfilePostsResult.Unreachable
            } else {
                val followed = following.isFollowing(viewer, cursor.actorUri)
                ProfilePostsResult.Found(
                    mergedRemotePage(viewer, cursor.actorUri, followed, outboxPage, cursor.storedBefore?.toPosition(), cursor.pinnedObjectUris, cursor.shownAtBoundary),
                )
            }
        }

        is ProfilePostCursor.Stored -> if (viewer == null) {
            ProfilePostsResult.NotLoggedIn
        } else {
            ProfilePostsResult.Found(storedOnlyPage(viewer, cursor.actorUri, cursor.before.toPosition(), cursor.pinnedObjectUris))
        }
    }

    /**
     * このサーバーのアカウントの投稿と、そのアカウントが付けたブーストを新しい順に並べる。ブーストは、ブーストした時刻の位置に置く。
     * 固定した投稿は、固定した順で最初のページの先頭に置き、続きには出さない。
     * 投稿とブーストはそれぞれ読んだところまでを続きの位置に持つので、どちらも抜けずに続きから読める
     *
     * @param cursor 続きの位置。最初のページなら null
     */
    private fun localProfilePosts(accountId: AccountId, username: String, viewer: AccountId?, cursor: ProfilePostCursor.Local?): ProfilePostPage {
        val pinnedNoteIds = pins.pinnedNotes(username)
        val pinnedNotes = if (cursor == null) notes.findByPublicIds(pinnedNoteIds.toSet()).let { found -> pinnedNoteIds.mapNotNull { found[it] } } else listOf()
        val pinnedNoteIdSet = pinnedNoteIds.toSet()
        val noteCandidates = notes.list(username, after = cursor?.notesAfter?.toNotePosition(), limit = PROFILE_POST_LIMIT)
        val boostCandidates = timeline.accountBoosts(booster = accountId, viewer = viewer, before = cursor?.boostsBefore?.toPosition(), limit = PROFILE_POST_LIMIT)
        val takenEntries = (
            noteCandidates.map { LocalProfileEntry.OwnNote(it) } + boostCandidates.map { LocalProfileEntry.Boost(it) }
            ).sortedByDescending { it.sortKey }.take(PROFILE_POST_LIMIT)
        val takenNotes = takenEntries.filterIsInstance<LocalProfileEntry.OwnNote>().map { it.note }
        val takenBoosts = takenEntries.filterIsInstance<LocalProfileEntry.Boost>().map { it.post }
        val recentNotes = takenNotes.filterNot { it.publicId in pinnedNoteIdSet }
        val localNotes = pinnedNotes + recentNotes
        val localNoteIds = localNotes.map { it.publicId }.toSet()
        val favouriteCounts = favourites.counts(localNoteIds)
        val stampCounts = stamps.counts(localNoteIds)
        val likedNoteIds = if (viewer == null) setOf() else likes.likedLocalNotes(viewer, localNoteIds)
        val boostedNoteIds = if (viewer == null) setOf() else accountBoosts.boostedLocalNotes(viewer, localNoteIds)
        val boostCounts = boosts.counts(localNoteIds)
        // 個別投稿画面はホームタイムラインに載る投稿しか開けず、このサーバーの投稿で載るのは自分の投稿と自分のブーストだけ
        val ownProfile = accountId == viewer
        val notePosts = localNotes.associate {
            val member = timeline.localPosition(it.publicId.value)?.member
            it.publicId to ProfilePost(
                contentHtml = it.contentHtml,
                publishedAt = it.publishedAt.toString(),
                summary = null,
                sensitive = false,
                linkPreviewId = LOCAL_NOTE_ID_PREFIX + it.publicId.value,
                postId = if (ownProfile) member else null,
                deletableNoteId = if (ownProfile) it.publicId.value else null,
                pinned = it.publicId in pinnedNoteIdSet,
                pinnableNoteId = if (ownProfile) it.publicId else null,
                images = it.images.map { image -> TimelinePostImage.Local(publicId = image.publicId, description = image.description) },
                emojis = listOf(),
                favouriteCount = favouriteCounts[it.publicId],
                stamps = stampCounts[it.publicId].orEmpty(),
                like = if (viewer == null || member == null) null else ProfilePostLike(postId = member, likedByMe = it.publicId in likedNoteIds),
                boostedByMe = if (viewer == null || member == null) null else it.publicId in boostedNoteIds,
                reactionPostId = if (viewer == null) null else member,
                noteRef = TimelineNoteRef.Local(it.publicId),
                boostCount = boostCounts[it.publicId],
                boostedNoteAuthor = null,
            )
        }
        val boostPosts = takenBoosts.mapNotNull { post ->
            val author = post.boostedNoteAuthor() ?: return@mapNotNull null
            Instant.ofEpochMilli(post.position.score) to post.toProfilePost(
                viewer = viewer,
                openable = ownProfile,
                boostedNoteAuthor = author,
            )
        }
        val timelinePosts = recentNotes.map { it.publishedAt to checkNotNull(notePosts[it.publicId]) } + boostPosts
        val hasMore = takenEntries.size < noteCandidates.size + boostCandidates.size ||
            noteCandidates.size == PROFILE_POST_LIMIT || boostCandidates.size == PROFILE_POST_LIMIT
        return ProfilePostPage(
            posts = pinnedNotes.map { checkNotNull(notePosts[it.publicId]) } + timelinePosts.sortedByDescending { it.first }.map { it.second },
            nextCursor = if (!hasMore) {
                null
            } else {
                ProfilePostCursor.Local(
                    username = username,
                    notesAfter = takenNotes.lastOrNull()?.let { StoredNotePosition(publishedAt = it.publishedAt.toString(), publicId = it.publicId.value) } ?: cursor?.notesAfter,
                    boostsBefore = takenBoosts.lastOrNull()?.position?.let { StoredTimelinePosition(score = it.score, member = it.member) } ?: cursor?.boostsBefore,
                )
            },
        )
    }

    private sealed interface LocalProfileEntry {
        val sortKey: Instant

        data class OwnNote(val note: Note) : LocalProfileEntry {
            override val sortKey: Instant get() = note.publishedAt
        }

        data class Boost(val post: TimelinePost) : LocalProfileEntry {
            override val sortKey: Instant get() = Instant.ofEpochMilli(post.position.score)
        }
    }

    /**
     * [linkPreviewId] がこのサーバーの投稿を指すなら、その本文。指さないか、投稿が無ければ null
     */
    fun localNoteContentHtml(linkPreviewId: String): String? {
        if (!linkPreviewId.startsWith(LOCAL_NOTE_ID_PREFIX)) return null
        return notes.find(PublicNoteId(linkPreviewId.removePrefix(LOCAL_NOTE_ID_PREFIX)))?.contentHtml
    }

    /**
     * 他のサーバーのユーザーの投稿は、開くたびに相手のサーバーの outbox から取り、保存はしない。保存済みの投稿は、いいねなどが付いた保存済みの方を使い、
     * outbox に載らないブーストやフォロワー限定の投稿は保存済みから補う。outbox を読めなければ、保存済みの投稿だけを出す。
     * 固定した投稿も保存していないので、開くたびに取りに行き、相手が並べた順で先頭に置く
     */
    private suspend fun firstRemotePostsPage(viewer: AccountId, actorUri: String, followed: Boolean): ProfilePostPage = coroutineScope {
        val featured = async { detached(FEATURED_POSTS_TIMEOUT, "固定した投稿", actorUri) { it.featuredNotes(actorUri) }.orEmpty() }
        val outboxPage = outboxPage(actorUri = actorUri, from = null)
        val featuredNotes = featured.await()
        val pinnedObjectUris = featuredNotes.map { it.objectUri }
        val page = if (outboxPage == null) {
            storedOnlyPage(viewer = viewer, actorUri = actorUri, before = null, pinnedObjectUris = pinnedObjectUris)
        } else {
            mergedRemotePage(
                viewer = viewer,
                actorUri = actorUri,
                followed = followed,
                outboxPage = outboxPage,
                storedBefore = null,
                pinnedObjectUris = pinnedObjectUris,
                shownAtBoundary = listOf(),
            )
        }
        page.copy(posts = withStored(viewer, followed, featuredNotes).map { it.second.copy(pinned = true) } + page.posts)
    }

    /**
     * @return 読めなければ null
     */
    private suspend fun outboxPage(actorUri: String, from: OutboxPosition?): RemoteNotesPage? =
        detached(OUTBOX_TIMEOUT, "outbox", actorUri) { it.recentNotesPage(actorUri, from) }

    /**
     * outbox の 1 ページに、そのページの範囲にある保存済みの投稿を混ぜる。
     * outbox を最後まで読んだら、それより古い保存済みの投稿は outbox に載らないものだけなので、[PROFILE_POST_LIMIT] 件ずつ保存済みから続ける
     *
     * @param storedBefore 保存済みの投稿をここより古い方から混ぜる。最初のページなら null
     * @param pinnedObjectUris 最初のページの先頭に固定した投稿として出した投稿。どのページでも一覧からは外す
     * @param shownAtBoundary 前のページで出した、前のページの境目と同じ時刻の投稿
     */
    private fun mergedRemotePage(
        viewer: AccountId,
        actorUri: String,
        followed: Boolean,
        outboxPage: RemoteNotesPage,
        storedBefore: TimelinePosition?,
        pinnedObjectUris: List<String>,
        shownAtBoundary: List<String>,
    ): ProfilePostPage {
        val outboxPosts = withStored(viewer, followed, outboxPage.notes)
        val lowestScore = outboxPage.notes.minOfOrNull { it.publishedAt.toEpochMilli() }
        val next = outboxPage.next
        val shownNoteRefs = (outboxPosts.map { it.second.noteRef } + shownAtBoundary.map { TimelineNoteRef.Remote(it) }).toSet()
        // 続きがあるときは、境目と同じ時刻の保存済みの投稿を次のページに回す。次の outbox のページに載っていれば、そちらと重ならずに済む。
        // member を空にすると時刻だけで区切れる
        val belowPage = when {
            lowestScore == null -> storedBefore
            next != null -> TimelinePosition(score = lowestScore + 1, member = "")
            else -> TimelinePosition(score = lowestScore, member = "")
        }
        val inPageRange = if (lowestScore == null) {
            listOf()
        } else {
            storedPostsDownTo(viewer, actorUri, before = storedBefore, lowestScore = if (next != null) lowestScore + 1 else lowestScore)
        }
        val olderThanOutbox = if (next == null) timeline.actorPosts(viewer, actorUri, before = belowPage, limit = PROFILE_POST_LIMIT) else listOf()
        val storedOnly = (inPageRange + olderThanOutbox)
            .filterNot { it.boostedBy == null && it.noteRef in shownNoteRefs }
            .mapNotNull { post -> post.toActorProfilePost(viewer)?.let { post.position.score to it } }
        val posts = (outboxPosts + storedOnly).sortedByDescending { it.first }.map { it.second }.withoutPinned(pinnedObjectUris)
        val lastOlder = olderThanOutbox.lastOrNull()?.position
        return ProfilePostPage(
            posts = posts,
            nextCursor = when {
                next != null -> ProfilePostCursor.Remote(
                    actorUri = actorUri,
                    outbox = next,
                    storedBefore = belowPage?.let { StoredTimelinePosition(it.score, it.member) },
                    pinnedObjectUris = pinnedObjectUris,
                    shownAtBoundary = outboxPage.notes.filter { it.publishedAt.toEpochMilli() == lowestScore }.map { it.objectUri },
                )

                olderThanOutbox.size == PROFILE_POST_LIMIT && lastOlder != null ->
                    ProfilePostCursor.Stored(
                        actorUri = actorUri,
                        before = StoredTimelinePosition(score = lastOlder.score, member = lastOlder.member),
                        pinnedObjectUris = pinnedObjectUris,
                    )

                else -> null
            },
        )
    }

    /**
     * outbox を読めないときに、保存済みの投稿だけで出す
     */
    private fun storedOnlyPage(viewer: AccountId, actorUri: String, before: TimelinePosition?, pinnedObjectUris: List<String>): ProfilePostPage {
        val stored = timeline.actorPosts(viewer, actorUri, before = before, limit = PROFILE_POST_LIMIT)
        val last = stored.lastOrNull()?.position
        return ProfilePostPage(
            posts = stored.mapNotNull { it.toActorProfilePost(viewer) }.withoutPinned(pinnedObjectUris),
            nextCursor = if (stored.size < PROFILE_POST_LIMIT || last == null) {
                null
            } else {
                ProfilePostCursor.Stored(actorUri = actorUri, before = StoredTimelinePosition(score = last.score, member = last.member), pinnedObjectUris = pinnedObjectUris)
            },
        )
    }

    /**
     * [before] より古く、[lowestScore] 以上の保存済みの投稿。outbox の 1 ページの範囲に収まる分だけを読む
     */
    private fun storedPostsDownTo(viewer: AccountId, actorUri: String, before: TimelinePosition?, lowestScore: Long): List<TimelinePost> =
        generateSequence(timeline.actorPosts(viewer, actorUri, before = before, limit = PROFILE_POST_LIMIT)) { previous ->
            val last = previous.lastOrNull()?.position
            if (previous.size < PROFILE_POST_LIMIT || last == null || last.score < lowestScore) {
                null
            } else {
                timeline.actorPosts(viewer, actorUri, before = last, limit = PROFILE_POST_LIMIT)
            }
        }.flatten().takeWhile { it.position.score >= lowestScore }.toList()

    /**
     * 相手のサーバーから取った投稿を、保存済みならいいねなどが付いた保存済みの方に替える。保存していなければ保存せずに出し、
     * いいねやブーストを押したときに保存できるよう [UnsavedRemoteNoteId] を持たせる
     *
     * @return 並べる時刻と投稿
     */
    private fun withStored(viewer: AccountId, followed: Boolean, notes: List<RemoteNoteInput>): List<Pair<Long, ProfilePost>> {
        val storedByUri = timeline.storedRemotePosts(viewer, notes.map { it.objectUri }.toSet())
            .associateBy { (it.noteRef as TimelineNoteRef.Remote).objectUri }
        return notes.map { note ->
            val stored = storedByUri[note.objectUri]
            // 個別投稿画面はホームタイムラインに載る投稿しか開けず、フォローしていない相手の投稿は載らない
            val post = stored?.toProfilePost(viewer = viewer, openable = followed, boostedNoteAuthor = null)?.withContentOf(note) ?: note.toUnsavedProfilePost()
            note.publishedAt.toEpochMilli() to post
        }
    }

    /**
     * 先頭に固定した投稿として出す投稿を、一覧から外す。ブーストとして載った投稿は、ブーストした時刻の位置に残す
     */
    private fun List<ProfilePost>.withoutPinned(pinnedObjectUris: List<String>): List<ProfilePost> {
        if (pinnedObjectUris.isEmpty()) return this
        val pinned = pinnedObjectUris.toSet()
        return filterNot { it.boostedNoteAuthor == null && (it.noteRef as? TimelineNoteRef.Remote)?.objectUri in pinned }
    }

    /**
     * 相手のサーバーが遅くてもプロフィールを待たせないよう期限を切る。
     *
     * 取得はこの呼び出しから切り離して動かす。ソケットの読み取りはスレッドの割り込みで止まらないことがあり、
     * 同じコルーチンで待つと期限を過ぎても読み取りのタイムアウトまで戻れない
     *
     * @return 取れないか、期限を過ぎたら null
     */
    private suspend fun <T> detached(timeout: Duration, label: String, actorUri: String, fetch: suspend (RemoteUserProfiles) -> T?): T? {
        val profiles = remoteProfiles ?: return null
        val fetching = remoteFetchScope.async {
            try {
                fetch(profiles)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.warn("${label}を取れなかった: $actorUri", exception)
                null
            }
        }
        val result = try {
            withTimeoutOrNull(timeout) { fetching.await() }
        } finally {
            fetching.cancel()
        }
        if (result == null) logger.warn("${label}を期限内に取れなかった: $actorUri")
        return result
    }

    /**
     * @return 投稿者のホストが分からないブーストは、誰の投稿か示せないので null
     */
    private fun TimelinePost.toActorProfilePost(viewer: AccountId): ProfilePost? {
        val author = if (boostedBy == null) null else boostedNoteAuthor() ?: return null
        return toProfilePost(viewer = viewer, openable = true, boostedNoteAuthor = author)
    }

    /**
     * 相手が後から編集していても最新の内容を出すよう、本文などは今回取った投稿から使う。いいねの状態や id は保存済みの方を残す
     */
    private fun ProfilePost.withContentOf(note: RemoteNoteInput): ProfilePost = copy(
        contentHtml = note.contentHtml,
        publishedAt = note.publishedAt.toString(),
        summary = note.summary,
        sensitive = note.sensitive,
        images = note.images,
        emojis = note.emojis,
    )

    private fun RemoteNoteInput.toUnsavedProfilePost(): ProfilePost {
        val unsavedId = UnsavedRemoteNoteId.of(objectUri)
        return ProfilePost(
            contentHtml = contentHtml,
            publishedAt = publishedAt.toString(),
            summary = summary,
            sensitive = sensitive,
            linkPreviewId = null,
            postId = null,
            deletableNoteId = null,
            pinned = false,
            pinnableNoteId = null,
            images = images,
            emojis = emojis,
            favouriteCount = null,
            stamps = listOf(),
            like = ProfilePostLike(postId = unsavedId, likedByMe = false),
            // ブーストできるのは公開の投稿だけ
            boostedByMe = if (visibility == "public") false else null,
            reactionPostId = unsavedId,
            noteRef = TimelineNoteRef.Remote(objectUri),
            boostCount = null,
            boostedNoteAuthor = null,
        )
    }

    /**
     * @param openable 個別投稿画面で開けるか
     */
    private fun TimelinePost.toProfilePost(viewer: AccountId?, openable: Boolean, boostedNoteAuthor: ProfilePostAuthor?): ProfilePost {
        val notePublicId = localPublicId
        return ProfilePost(
            contentHtml = contentHtml,
            publishedAt = publishedAt.toString(),
            summary = summary,
            sensitive = sensitive,
            linkPreviewId = when {
                notePublicId != null -> LOCAL_NOTE_ID_PREFIX + notePublicId.value

                // OGP はタイムラインの投稿として引くので、見ている人のタイムラインで開けない投稿は引けない
                openable -> position.member

                else -> null
            },
            postId = if (openable) position.member else null,
            // ブーストとして載せた投稿は、ブーストされた元の投稿を消す場所ではない
            deletableNoteId = if (boostedBy == null) notePublicId?.value else null,
            pinned = false,
            pinnableNoteId = null,
            images = images,
            emojis = emojis,
            favouriteCount = favouriteCount,
            stamps = stamps,
            like = if (viewer == null) null else ProfilePostLike(postId = reactionMember, likedByMe = likedByMe),
            boostedByMe = if (viewer == null) null else boostedByMe,
            reactionPostId = if (viewer == null) null else reactionMember,
            noteRef = noteRef,
            boostCount = boostCount,
            boostedNoteAuthor = boostedNoteAuthor,
        )
    }

    /**
     * @return 他のサーバーの投稿者のホストが分からなければ null
     */
    private fun TimelinePost.boostedNoteAuthor(): ProfilePostAuthor? {
        val authorHost = if (actorUri == null) localDomain else URI(actorUri).host ?: return null
        return ProfilePostAuthor(
            actorUri = actorUri,
            host = authorHost,
            username = username,
            displayName = displayName,
            iconUrl = actorIconUrl,
            displayNameEmojis = actorEmojis,
            updatedAt = actorUpdatedAt,
        )
    }

    private companion object {
        const val PROFILE_POST_LIMIT = 20

        val FEATURED_POSTS_TIMEOUT: Duration = 3.seconds

        val OUTBOX_TIMEOUT: Duration = 5.seconds

        /**
         * このサーバーの投稿は公開されているので、タイムラインの id ではなく公開 id で OGP を引けるようにする
         */
        const val LOCAL_NOTE_ID_PREFIX = "note:"
    }
}

interface RemoteUserProfiles {
    suspend fun find(username: String, host: String): RemoteUserProfile?

    suspend fun findByActorUri(actorUri: String): RemoteUserProfile?

    /**
     * 相手のサーバーの outbox から、公開している投稿を新しい方から少しずつ取る
     *
     * @param from 読み始める位置。null なら最初から
     * @return 読めなければ null
     */
    suspend fun recentNotesPage(actorUri: String, from: OutboxPosition?): RemoteNotesPage?

    /**
     * 相手がプロフィールに固定した投稿。相手が並べた順
     */
    suspend fun featuredNotes(actorUri: String): List<RemoteNoteInput>
}

/**
 * @param headerUrl ヘッダー画像の取得元。中継する前の相手の URL
 */
data class RemoteUserProfile(
    val user: RemoteUser,
    val headerUrl: String?,
    val biography: String?,
)

/**
 * @param headerUrl ヘッダー画像の取得元。中継する前の相手の URL。このサーバーのユーザーは設定できないので null
 * @param biography プレーンテキスト
 * @param links 他のサーバーのユーザーのものは受け取っていないので空
 * @param viewerIsOwner 見ているアカウント本人のプロフィールか。他のサーバーのユーザーは本人になりえないので false
 */
data class UserProfile(
    val user: SearchedUser,
    val following: Boolean,
    val headerUrl: String?,
    val biography: String?,
    val links: List<ProfileLink>,
    val viewerIsOwner: Boolean,
)

/**
 * プロフィールを開く相手。`@` は外し、ホストが無ければこのサーバーのユーザーとする
 */
private data class ProfileAcct(
    val username: String,
    val host: String,
    val isLocal: Boolean,
) {
    companion object {
        /**
         * @return ユーザー名が無ければ null
         */
        fun parse(acct: String, localDomain: String): ProfileAcct? {
            val normalizedAcct = acct.removePrefix("@")
            val username = normalizedAcct.substringBefore('@')
            if (username.isEmpty()) return null
            val host = normalizedAcct.substringAfter('@', "").ifEmpty { localDomain }
            return ProfileAcct(username = username, host = host, isLocal = host.equals(localDomain, ignoreCase = true))
        }
    }
}

/**
 * @param notes 新しい順
 * @param next 続きの位置。最後まで読んだら null
 */
data class RemoteNotesPage(
    val notes: List<RemoteNoteInput>,
    val next: OutboxPosition?,
)

/**
 * 相手の outbox のどこまで読んだか
 *
 * @param pageId 次に読むページ
 * @param itemOffset そのページの何件目から読むか
 */
@Serializable
data class OutboxPosition(
    @SerialName("pageId") val pageId: String,
    @SerialName("itemOffset") val itemOffset: Int,
)

/**
 * @param nextCursor 続きを [UserProfileService.morePosts] で読むときに渡す。続きが無ければ null
 */
data class ProfilePostPage(
    val posts: List<ProfilePost>,
    val nextCursor: ProfilePostCursor?,
)

/**
 * プロフィールの投稿をどこまで返したか。外には [encode] した文字列で渡す
 */
@Serializable
sealed interface ProfilePostCursor {
    /**
     * このサーバーのアカウント。投稿とブーストを、それぞれ返したところまで持つ
     */
    @Serializable
    @SerialName("local")
    data class Local(
        @SerialName("username") val username: String,
        @SerialName("notesAfter") val notesAfter: StoredNotePosition?,
        @SerialName("boostsBefore") val boostsBefore: StoredTimelinePosition?,
    ) : ProfilePostCursor

    /**
     * 他のサーバーのユーザー。outbox の続きと、混ぜる保存済みの投稿の続きを持つ
     *
     * @param pinnedObjectUris 最初のページの先頭に固定した投稿として出した投稿。続きにも同じ投稿を出さないよう持ち回る
     * @param shownAtBoundary 前のページの outbox の境目と同じ時刻で、前のページに出した投稿。同じ時刻の保存済みの投稿を次のページに回すので、重ならないよう外す
     */
    @Serializable
    @SerialName("remote")
    data class Remote(
        @SerialName("actorUri") val actorUri: String,
        @SerialName("outbox") val outbox: OutboxPosition,
        @SerialName("storedBefore") val storedBefore: StoredTimelinePosition?,
        @SerialName("pinnedObjectUris") val pinnedObjectUris: List<String>,
        @SerialName("shownAtBoundary") val shownAtBoundary: List<String>,
    ) : ProfilePostCursor

    /**
     * 他のサーバーのユーザーで、outbox を読めなかったので保存済みの投稿だけを出しているとき
     */
    @Serializable
    @SerialName("stored")
    data class Stored(
        @SerialName("actorUri") val actorUri: String,
        @SerialName("before") val before: StoredTimelinePosition,
        @SerialName("pinnedObjectUris") val pinnedObjectUris: List<String>,
    ) : ProfilePostCursor

    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(Json.encodeToString(serializer(), this).toByteArray())

    companion object {
        /**
         * 読めなければ null
         */
        fun decode(encoded: String): ProfilePostCursor? =
            runCatching { Json.decodeFromString(serializer(), Base64.getUrlDecoder().decode(encoded).decodeToString()) }.getOrNull()
    }
}

/**
 * @param publishedAt [Instant] の文字列。桁を落とさないよう文字列で持つ
 */
@Serializable
data class StoredNotePosition(
    @SerialName("publishedAt") val publishedAt: String,
    @SerialName("publicId") val publicId: String,
) {
    fun toNotePosition(): NotePosition = NotePosition(publishedAt = Instant.parse(publishedAt), publicId = PublicNoteId(publicId))
}

@Serializable
data class StoredTimelinePosition(
    @SerialName("score") val score: Long,
    @SerialName("member") val member: String,
) {
    fun toPosition(): TimelinePosition = TimelinePosition(score = score, member = member)
}

sealed interface ProfilePostsResult {
    data class Found(val page: ProfilePostPage) : ProfilePostsResult

    data object NotLoggedIn : ProfilePostsResult

    data object NotFound : ProfilePostsResult

    /**
     * 相手のサーバーから読めなかった
     */
    data object Unreachable : ProfilePostsResult
}

/**
 * @param linkPreviewId UserInfo.linkPreviews に渡せる id。OGP を取れない投稿は null
 * @param deletableNoteId 見ているアカウントの投稿なら deleteNote に渡す id。消せない投稿は null
 * @param pinned プロフィールの先頭に固定されているか
 * @param pinnableNoteId 見ているアカウントのプロフィールの投稿なら setPinned に渡す id。固定できない投稿は null
 * @param images URL にはしていない画像
 * @param favouriteCount 他のサーバーから届いたお気に入りの数。他のサーバーの投稿は数を受け取らないので null
 * @param stamps 他のサーバーから届いたスタンプ。他のサーバーの投稿は受け取らないので空
 * @param like いいねできない投稿は null
 * @param boostedByMe 見ているアカウントがブーストしているか。ログインしていないか、ブーストできない投稿は null。付け外しには [reactionPostId] を渡す
 * @param reactionPostId いいねやブーストを付け外しするときに渡す、タイムラインの投稿の id。ブーストした投稿は、ブーストされた元の投稿を指す。
 *   保存していない他のサーバーの投稿は [UnsavedRemoteNoteId] の id で、押したときに保存する。
 *   ログインしていないか、付け外しできない投稿は null
 * @param noteRef 投稿を外から指すための元
 * @param boostCount 他のサーバーから届いたブーストの数。他のサーバーの投稿は数を受け取らないので null
 * @param boostedNoteAuthor プロフィールの本人がブーストした投稿なら、その投稿者。本人の投稿は null
 */
data class ProfilePost(
    val contentHtml: String,
    val publishedAt: String,
    val summary: String?,
    val sensitive: Boolean,
    val linkPreviewId: String?,
    val postId: String?,
    val deletableNoteId: String?,
    val pinned: Boolean,
    val pinnableNoteId: PublicNoteId?,
    val images: List<TimelinePostImage>,
    val emojis: List<CustomEmojiRecord>,
    val favouriteCount: Long?,
    val stamps: List<StampCount>,
    val noteRef: TimelineNoteRef,
    val boostCount: Long?,
    val like: ProfilePostLike?,
    val boostedByMe: Boolean?,
    val reactionPostId: String?,
    val boostedNoteAuthor: ProfilePostAuthor?,
)

/**
 * @param actorUri 他のサーバーの投稿者のアクター。このサーバーのアカウントは null
 * @param username 相手の preferredUsername。無ければアクターの URI
 * @param iconUrl 他のサーバーの投稿者のプロフィール画像の URL。無いか、このサーバーのアカウントなら null
 * @param updatedAt 他のサーバーの投稿者の情報を最後に受け取った時刻。このサーバーのアカウントは null
 */
data class ProfilePostAuthor(
    val actorUri: String?,
    val host: String,
    val username: String,
    val displayName: String?,
    val iconUrl: String?,
    val displayNameEmojis: List<CustomEmojiRecord>,
    val updatedAt: Instant?,
)

/**
 * @param postId いいねを付け外しするときに渡す、タイムラインの投稿の id。保存していない他のサーバーの投稿は [UnsavedRemoteNoteId] の id
 */
data class ProfilePostLike(
    val postId: String,
    val likedByMe: Boolean,
)
