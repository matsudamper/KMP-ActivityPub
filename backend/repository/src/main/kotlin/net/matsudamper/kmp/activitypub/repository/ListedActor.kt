package net.matsudamper.kmp.activitypub.repository

import java.time.Instant

/**
 * フォロー・フォロワーの一覧の位置。同じ時刻の相手がページの境目に来ても落ちないよう、アクター URL も並び順に使う
 */
data class ActorListPosition(
    val listedAt: Instant,
    val actorUri: String,
)

/**
 * フォロー・フォロワーの一覧の 1 人
 *
 * @param listedAt 一覧の並び順に使う時刻。フォローならフォローした時刻、フォロワーなら成立した時刻
 * @param preferredUsername 相手のプロフィールを受け取っていなければ null。フォローした相手は、
 *   こちらをフォローしてきたときにしかプロフィールを受け取らない
 */
data class ListedActor(
    val actorUri: String,
    val listedAt: Instant,
    val preferredUsername: String?,
    val displayName: String?,
    val iconUrl: String?,
    val actorUpdatedAt: Instant?,
)
