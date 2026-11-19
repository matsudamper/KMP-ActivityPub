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

    /**
     * 相手に送った Block の id。ブロックしていないか、まだ送っていなければ null
     */
    fun findBlockActivityId(accountId: AccountId, actorUri: String): String?

    /**
     * 送る Block の id を記録して、配信キューに積む。記録と投函を分けると、解除のときに取り消す Block が分からなくなる
     *
     * @param blockActivityId [body] の Block の id
     * @return ブロックしていなければ何もせず false
     */
    fun recordBlockActivity(accountId: AccountId, actorUri: String, blockActivityId: String, inbox: String, body: String, now: Instant): Boolean
}

/**
 * @param acct 一覧に出す username@host
 */
data class BlockedActor(
    val actorUri: String,
    val acct: String,
)
