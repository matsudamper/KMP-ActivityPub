package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.LikedNotePosition
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.slf4j.LoggerFactory

/**
 * プロフィールの「いいね」に出す、そのユーザーがいいねした投稿。
 *
 * このサーバーのユーザーは、本人か、いいねを公開しているときだけ出す。他のサーバーのユーザーは、Actor の liked を読めたときだけ出す。
 * どちらも見ているアカウントが開ける投稿に限る
 */
class ProfileLikesService(
    private val accounts: AccountRepository,
    private val remoteActors: RemoteActorRepository,
    private val timeline: TimelineRepository,
    private val remoteProfiles: RemoteUserProfiles?,
    private val remoteLikes: RemoteLikedCollections?,
    private val unsavedNotes: UnsavedRemoteNotes?,
    private val localDomain: String,
) {
    private val logger = LoggerFactory.getLogger(ProfileLikesService::class.java)
    private val remoteFetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 最初のページ。いいねを出せない相手なら [ProfileLikesResult.Hidden] で、プロフィールに「いいね」を出さない
     */
    suspend fun firstPage(acct: String, viewer: AccountId?): ProfileLikesResult {
        // 他のサーバーの投稿を開くのと同じく、ログインしている人にだけ出す
        if (viewer == null) return ProfileLikesResult.Hidden
        val target = ProfileAcct.parse(acct, localDomain) ?: return ProfileLikesResult.Hidden
        if (target.isLocal) {
            val account = accounts.findByUsername(target.username) ?: return ProfileLikesResult.Hidden
            val likesPublic = accounts.findProfileDetail(account.username)?.likesPublic == true
            if (!likesPublic && account.id != viewer) return ProfileLikesResult.Hidden
            return ProfileLikesResult.Found(localPage(liker = account.id, username = account.username, viewer = viewer, before = null))
        }
        val actorUri = remoteActors.findByAcct(target.username, target.host)?.actorUri
            ?: remoteProfiles?.find(target.username, target.host)?.user?.actorUri
            ?: return ProfileLikesResult.Hidden
        return when (val objects = remoteLikedObjects(actorUri = actorUri, from = null)) {
            is RemoteLikedObjects.Found -> ProfileLikesResult.Found(remotePage(viewer = viewer, actorUri = actorUri, objects = objects))

            // 最初のページが読めないときは、liked を出していないのと同じく「いいね」を出さない
            RemoteLikedObjects.Absent, RemoteLikedObjects.Unavailable -> ProfileLikesResult.Hidden
        }
    }

    /**
     * [ProfileLikePage.nextCursor] から先
     */
    suspend fun morePage(viewer: AccountId?, cursor: ProfileLikeCursor): ProfileLikesResult {
        if (viewer == null) return ProfileLikesResult.Hidden
        return when (cursor) {
            is ProfileLikeCursor.Local -> {
                val account = accounts.findByUsername(cursor.username) ?: return ProfileLikesResult.Hidden
                val likesPublic = accounts.findProfileDetail(account.username)?.likesPublic == true
                if (!likesPublic && account.id != viewer) return ProfileLikesResult.Hidden
                val before = LikedNotePosition(likedAt = Instant.parse(cursor.likedAt), member = cursor.member)
                ProfileLikesResult.Found(localPage(liker = account.id, username = account.username, viewer = viewer, before = before))
            }

            is ProfileLikeCursor.Remote -> when (val objects = remoteLikedObjects(actorUri = cursor.actorUri, from = cursor.liked)) {
                is RemoteLikedObjects.Found -> ProfileLikesResult.Found(remotePage(viewer = viewer, actorUri = cursor.actorUri, objects = objects))
                RemoteLikedObjects.Absent -> ProfileLikesResult.Hidden
                RemoteLikedObjects.Unavailable -> ProfileLikesResult.Unreachable
            }
        }
    }

    private fun localPage(liker: AccountId, username: String, viewer: AccountId, before: LikedNotePosition?): ProfileLikePage {
        val liked = timeline.likedPosts(liker = liker, viewer = viewer, before = before, limit = PAGE_SIZE)
        val last = liked.lastOrNull()?.position
        return ProfileLikePage(
            posts = liked.map { it.post },
            nextCursor = if (liked.size < PAGE_SIZE || last == null) {
                null
            } else {
                ProfileLikeCursor.Local(username = username, likedAt = last.likedAt.toString(), member = last.member)
            },
        )
    }

    /**
     * 相手が並べた順で返す。保存していない投稿は取ってきて保存する。開けない投稿と、取れなかった投稿は出さない
     */
    private suspend fun remotePage(viewer: AccountId, actorUri: String, objects: RemoteLikedObjects.Found): ProfileLikePage {
        val localNoteUriPrefix = "https://$localDomain/notes/"
        val localPublicIds = objects.objectUris.associateWith { uri ->
            uri.takeIf { it.startsWith(localNoteUriPrefix) }?.removePrefix(localNoteUriPrefix)?.takeIf { it.isNotEmpty() }?.let { PublicNoteId(it) }
        }
        val localPosts = timeline.localPositionsByPublicId(localPublicIds.values.filterNotNull().toSet())
            .mapNotNull { (publicId, position) -> timeline.viewablePost(viewer, position.member)?.let { publicId to it } }
            .toMap()
        val remoteUris = localPublicIds.filterValues { it == null }.keys
        val stored = storedPostsByUri(viewer, remoteUris)
        val imported = storedPostsByUri(viewer, importMissing(viewer, remoteUris - stored.keys))
        val postsByUri = stored + imported
        return ProfileLikePage(
            posts = objects.objectUris.mapNotNull { uri ->
                val localPublicId = localPublicIds[uri]
                if (localPublicId != null) localPosts[localPublicId] else postsByUri[uri]
            }.filterNot { it.deleted },
            nextCursor = objects.next?.let { ProfileLikeCursor.Remote(actorUri = actorUri, liked = it) },
        )
    }

    private fun storedPostsByUri(viewer: AccountId, objectUris: Set<String>): Map<String, TimelinePost> =
        timeline.storedRemotePosts(viewer, objectUris).associateBy { (it.noteRef as TimelineNoteRef.Remote).objectUri }

    /**
     * 相手のサーバーが遅くても一覧を待たせないよう、まとめて期限を切る。期限までに保存できた投稿だけを出す
     *
     * @return 保存できた投稿の id
     */
    private suspend fun importMissing(viewer: AccountId, objectUris: Set<String>): Set<String> {
        val notes = unsavedNotes ?: return setOf()
        if (objectUris.isEmpty()) return setOf()
        val importing = objectUris.map { objectUri ->
            remoteFetchScope.async {
                try {
                    objectUri.takeIf { notes.import(viewer, objectUri) != null }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    logger.warn("いいねした投稿を取れなかった: $objectUri", exception)
                    null
                }
            }
        }
        return try {
            withTimeoutOrNull(IMPORT_TIMEOUT) { importing.joinAll() }
            importing.filter { it.isCompleted && !it.isCancelled }.mapNotNull { it.await() }.toSet()
        } finally {
            // 呼び出し元が取り消されたときも、別のスコープで動く取得を残さない
            importing.forEach { it.cancel() }
        }
    }

    /**
     * 取得はこの呼び出しから切り離して動かす。ソケットの読み取りはスレッドの割り込みで止まらないことがあり、
     * 同じコルーチンで待つと期限を過ぎても読み取りのタイムアウトまで戻れない
     */
    private suspend fun remoteLikedObjects(actorUri: String, from: OutboxPosition?): RemoteLikedObjects {
        val likes = remoteLikes ?: return RemoteLikedObjects.Absent
        val fetching = remoteFetchScope.async {
            try {
                likes.page(actorUri = actorUri, from = from, limit = PAGE_SIZE)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.warn("いいねの一覧を取れなかった: $actorUri", exception)
                RemoteLikedObjects.Unavailable
            }
        }
        val result = try {
            withTimeoutOrNull(LIKED_TIMEOUT) { fetching.await() }
        } finally {
            fetching.cancel()
        }
        if (result == null) logger.warn("いいねの一覧を期限内に取れなかった: $actorUri")
        return result ?: RemoteLikedObjects.Unavailable
    }

    private companion object {
        const val PAGE_SIZE = 20

        val LIKED_TIMEOUT: Duration = 5.seconds

        val IMPORT_TIMEOUT: Duration = 8.seconds
    }
}

/**
 * 相手のアクターの liked を読む
 */
fun interface RemoteLikedCollections {
    /**
     * @param from 読み始める位置。null なら最初から
     */
    suspend fun page(actorUri: String, from: OutboxPosition?, limit: Int): RemoteLikedObjects
}

sealed interface RemoteLikedObjects {
    /**
     * @param objectUris いいねした投稿の id。相手が並べた順
     * @param next 続きの位置。最後まで読んだら null
     */
    data class Found(val objectUris: List<String>, val next: OutboxPosition?) : RemoteLikedObjects

    /** 相手が liked を出していない */
    data object Absent : RemoteLikedObjects

    /** アクターか liked を読めなかった */
    data object Unavailable : RemoteLikedObjects
}

/**
 * @param posts いいねした新しい順。他のサーバーのユーザーは相手が並べた順
 * @param nextCursor 続きを [ProfileLikesService.morePage] で読むときに渡す。続きが無ければ null
 */
data class ProfileLikePage(
    val posts: List<TimelinePost>,
    val nextCursor: ProfileLikeCursor?,
)

sealed interface ProfileLikesResult {
    data class Found(val page: ProfileLikePage) : ProfileLikesResult

    /** 公開していない、liked を出していない、ログインしていない、見つからない。プロフィールに「いいね」を出さない */
    data object Hidden : ProfileLikesResult

    /** 続きを相手のサーバーから読めなかった */
    data object Unreachable : ProfileLikesResult
}

/**
 * いいねの一覧をどこまで返したか。外には [encode] した文字列で渡す
 */
@Serializable
sealed interface ProfileLikeCursor {
    /**
     * @param likedAt [Instant] の文字列。桁を落とさないよう文字列で持つ
     */
    @Serializable
    @SerialName("local")
    data class Local(
        @SerialName("username") val username: String,
        @SerialName("likedAt") val likedAt: String,
        @SerialName("member") val member: String,
    ) : ProfileLikeCursor

    @Serializable
    @SerialName("remote")
    data class Remote(
        @SerialName("actorUri") val actorUri: String,
        @SerialName("liked") val liked: OutboxPosition,
    ) : ProfileLikeCursor

    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(Json.encodeToString(serializer(), this).toByteArray())

    companion object {
        /**
         * 読めなければ null
         */
        fun decode(encoded: String): ProfileLikeCursor? =
            runCatching { Json.decodeFromString(serializer(), Base64.getUrlDecoder().decode(encoded).decodeToString()) }.getOrNull()
    }
}
