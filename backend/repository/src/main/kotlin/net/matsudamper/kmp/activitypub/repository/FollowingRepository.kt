package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

interface FollowingRepository {
    fun contains(accountId: AccountId, actorUri: String): Boolean
    fun add(accountId: AccountId, actorUri: String, createdAt: Instant): Boolean
    fun remove(accountId: AccountId, actorUri: String): Boolean

    /**
     * フォローした新しい順に、[after] より後ろから [limit] 件まで返す
     */
    fun list(accountId: AccountId, after: FollowingPosition?, limit: Int): List<FollowedActor>
}

/**
 * 同じ時刻にフォローした相手がページの境目に来ても落ちないよう、アクター URL も並び順に使う
 */
data class FollowingPosition(
    val createdAt: Instant,
    val actorUri: String,
)

/**
 * 相手のプロフィールは、こちらをフォローしてきたときに受け取ったものしか無い。受け取っていなければ null
 */
data class FollowedActor(
    val actorUri: String,
    val createdAt: Instant,
    val preferredUsername: String?,
    val displayName: String?,
    val iconUrl: String?,
    val actorUpdatedAt: Instant?,
)
