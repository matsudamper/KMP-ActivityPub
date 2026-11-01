package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.time.Clock
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.FollowedActor
import net.matsudamper.kmp.activitypub.repository.FollowingPosition
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

class FollowingService(
    private val accounts: AccountRepository,
    private val following: FollowingRepository,
    private val delivery: FollowDelivery,
    private val localDomain: String,
    private val clock: Clock,
) {
    fun isFollowing(accountId: AccountId?, actorUri: String?): Boolean =
        accountId != null && actorUri != null && following.contains(accountId, actorUri)

    fun setFollowing(accountId: AccountId?, actorUri: String, followed: Boolean): Boolean {
        if (accountId == null || accounts.findById(accountId) == null) return false
        val uri = runCatching { URI(actorUri) }.getOrNull() ?: return false
        if (uri.scheme !in listOf("https", "http") || uri.host == null || uri.host.equals(localDomain, ignoreCase = true)) return false
        val changed = if (followed) {
            following.add(accountId, actorUri, clock.instant())
        } else {
            following.remove(accountId, actorUri)
        }
        if (changed) delivery.send(accountId, actorUri, followed)
        return true
    }

    /**
     * フォローした新しい順に 1 ページ分返す
     */
    fun followingUsers(accountId: AccountId, cursor: FollowingCursor?): FollowingUserPage {
        val actors = following.list(accountId, after = cursor?.toPosition(), limit = PAGE_SIZE + 1)
        val page = actors.take(PAGE_SIZE)
        val last = page.lastOrNull()
        return FollowingUserPage(
            users = page.mapNotNull { it.toFollowingUser() },
            nextCursor =
            if (actors.size > PAGE_SIZE && last != null) {
                FollowingCursor(createdAt = last.createdAt.toString(), actorUri = last.actorUri)
            } else {
                null
            },
        )
    }

    /**
     * ホストが読めない URL はフォローするときに弾いているので、ここで落ちるものは無い想定
     */
    private fun FollowedActor.toFollowingUser(): FollowingUser? {
        val host = runCatching { URI(actorUri).host }.getOrNull() ?: return null
        return FollowingUser(
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

data class FollowingUserPage(
    val users: List<FollowingUser>,
    val nextCursor: FollowingCursor?,
)

/**
 * @param username 相手のプロフィールを受け取っていなければ null
 * @param displayName 未設定か、相手のプロフィールを受け取っていなければ null
 */
data class FollowingUser(
    val actorUri: String,
    val username: String?,
    val host: String,
    val displayName: String?,
    val iconUrl: String?,
    val updatedAt: Instant?,
)

@Serializable
data class FollowingCursor(
    @SerialName("createdAt")
    val createdAt: String,
    @SerialName("actorUri")
    val actorUri: String,
) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(Json.encodeToString(serializer(), this).toByteArray())

    fun toPosition(): FollowingPosition = FollowingPosition(createdAt = Instant.parse(createdAt), actorUri = actorUri)

    companion object {
        /**
         * 読めなければ null
         */
        fun decode(encoded: String): FollowingCursor? =
            runCatching {
                Json.decodeFromString(serializer(), Base64.getUrlDecoder().decode(encoded).decodeToString())
                    .also { Instant.parse(it.createdAt) }
            }.getOrNull()
    }
}

interface FollowDelivery {
    fun send(accountId: AccountId, actorUri: String, followed: Boolean)
}

/** 第一段階では内部保存だけを行うため、Follow / Undo を相手の inbox へ送らない。 */
object NoOpFollowDelivery : FollowDelivery {
    override fun send(accountId: AccountId, actorUri: String, followed: Boolean) = Unit
}
