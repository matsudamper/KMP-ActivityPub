package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.ActorListPosition
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.repository.ListedActor
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * ログイン中のアカウントのフォロー・フォロワーの数と一覧
 */
class FollowListService(
    private val accounts: AccountRepository,
    private val following: FollowingRepository,
    private val followers: FollowerRepository,
) {
    /**
     * アカウントが無ければ null
     */
    fun counts(accountId: AccountId): FollowCounts? {
        val account = accounts.findById(accountId) ?: return null
        return FollowCounts(
            following = following.count(accountId),
            followers = followers.countAcceptedFollowers(account.username),
        )
    }

    /**
     * フォローした新しい順に 1 ページ分返す
     */
    fun followingUsers(accountId: AccountId, cursor: ActorListCursor?): ActorListPage =
        createPage(following.list(accountId, after = cursor?.toPosition(), limit = PAGE_SIZE + 1))

    /**
     * フォローが成立した新しい順に 1 ページ分返す。アカウントが無ければ null
     */
    fun followerUsers(accountId: AccountId, cursor: ActorListCursor?): ActorListPage? {
        val account = accounts.findById(accountId) ?: return null
        return createPage(followers.listAcceptedFollowers(account.username, after = cursor?.toPosition(), limit = PAGE_SIZE + 1))
    }

    /**
     * @param actors 1 ページ分より 1 件多く取ったもの。多く取れたら続きがある
     */
    private fun createPage(actors: List<ListedActor>): ActorListPage {
        val page = actors.take(PAGE_SIZE)
        val last = page.lastOrNull()
        return ActorListPage(
            users = page.mapNotNull { it.toListedUser() },
            nextCursor =
            if (actors.size > PAGE_SIZE && last != null) {
                ActorListCursor(listedAt = last.listedAt.toString(), actorUri = last.actorUri)
            } else {
                null
            },
        )
    }

    /**
     * ホストが読めないアクターは acct を作れないので出さない
     */
    private fun ListedActor.toListedUser(): ListedUser? {
        val host = runCatching { URI(actorUri).host }.getOrNull() ?: return null
        return ListedUser(
            actorUri = actorUri,
            username = preferredUsername,
            host = host.lowercase(),
            displayName = displayName?.takeIf { it.isNotBlank() },
            iconUrl = iconUrl,
            updatedAt = actorUpdatedAt,
        )
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}

data class FollowCounts(
    val following: Long,
    val followers: Long,
)

data class ActorListPage(
    val users: List<ListedUser>,
    val nextCursor: ActorListCursor?,
)

/**
 * @param username 相手のプロフィールを受け取っていなければ null
 * @param displayName 未設定か、相手のプロフィールを受け取っていなければ null
 */
data class ListedUser(
    val actorUri: String,
    val username: String?,
    val host: String,
    val displayName: String?,
    val iconUrl: String?,
    val updatedAt: Instant?,
)

@Serializable
data class ActorListCursor(
    @SerialName("listedAt")
    val listedAt: String,
    @SerialName("actorUri")
    val actorUri: String,
) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(Json.encodeToString(serializer(), this).toByteArray())

    fun toPosition(): ActorListPosition = ActorListPosition(listedAt = Instant.parse(listedAt), actorUri = actorUri)

    companion object {
        /**
         * 読めなければ null
         */
        fun decode(encoded: String): ActorListCursor? =
            runCatching {
                val cursor = Json.decodeFromString(serializer(), Base64.getUrlDecoder().decode(encoded).decodeToString())
                cursor.toPosition()
                cursor
            }.getOrNull()
    }
}
