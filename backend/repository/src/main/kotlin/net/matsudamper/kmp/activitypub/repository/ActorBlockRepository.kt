package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * アカウントごとにブロックした他のサーバーのアクター
 */
interface ActorBlockRepository {
    /**
     * 新しくブロックした順に返す
     */
    fun list(accountId: AccountId): List<BlockedActor>

    /**
     * 既にブロックしていれば acct だけ書き換える
     */
    fun add(accountId: AccountId, actor: BlockedActor, createdAt: Instant)

    fun remove(accountId: AccountId, actorUri: String)
}

/**
 * @param acct 一覧に出す username@host
 */
data class BlockedActor(
    val actorUri: String,
    val acct: String,
)
