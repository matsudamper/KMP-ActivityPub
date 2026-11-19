package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * ブロックしたことを相手のサーバーに伝える。ブロックしたかどうかの記録は呼ぶ側が先に書いておく
 */
interface BlockDelivery {
    /**
     * 相手に Block を積み、ブロックした相手からのフォローを外して Reject を積む。
     * Block はまだ送っていないときだけ積む
     */
    suspend fun blockActor(accountId: AccountId, actorUri: String)

    /**
     * ブロックしたサーバーやアクターからのフォローを外して、Reject を積む
     */
    fun rejectBlockedFollowers(accountId: AccountId)

    /**
     * ブロックしたときに送った Block を取り消す Undo を積む
     */
    suspend fun undoBlock(accountId: AccountId, actorUri: String, blockActivityId: String)
}
