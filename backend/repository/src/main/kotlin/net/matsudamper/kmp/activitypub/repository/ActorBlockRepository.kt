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

    /**
     * ブロックを外す。Block を送っていれば、取り消す Undo を Block を送った inbox に積む。
     * まだ一度も送っていない Block は消すだけで Undo は積まない。再送を待っている Block は消し、送っている途中の Block は失敗しても送り直さない。
     *
     * 外すのと投函は分けない。分けると、間に記録された Block や、投函に失敗した Block を取り消せなくなる。
     * 残った Block が Undo を追い越すと、解除したのに相手にはブロックが残る
     *
     * @param undoBody 送った Block の id から Undo のアクティビティを作る。null なら Undo は積まない
     * @return Undo を積んだら true
     */
    fun remove(accountId: AccountId, actorUri: String, now: Instant, undoBody: (blockActivityId: String) -> String?): Boolean

    /**
     * 相手に送った Block の id。ブロックしていないか、まだ送っていなければ null
     */
    fun findBlockActivityId(accountId: AccountId, actorUri: String): String?

    /**
     * 送る Block の id を記録して、配信キューに積む。記録と投函を分けると、解除のときに取り消す Block が分からなくなる
     *
     * @param blockActivityId [body] の Block の id
     * 既に Block を記録していれば積まない。同じ相手へのブロックが並ぶと、上書きした方の Block しか解除で取り消せなくなる
     *
     * @return ブロックしていないか、既に Block を記録していれば何もせず false
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
