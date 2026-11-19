package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 相手のサーバーには何も送らず、呼ばれたことだけを残す
 */
internal class RecordingBlockDelivery : BlockDelivery {
    val blockedActors = mutableListOf<String>()
    val rejectedAccounts = mutableListOf<AccountId>()
    val undoneBlockActivityIds = mutableListOf<String>()
    var enqueuedCount = 0

    override suspend fun blockActor(accountId: AccountId, actorUri: String) {
        blockedActors += actorUri
    }

    override fun rejectBlockedFollowers(accountId: AccountId) {
        rejectedAccounts += accountId
    }

    override fun undoBlockBody(accountId: AccountId, actorUri: String, blockActivityId: String): String {
        undoneBlockActivityIds += blockActivityId
        return "undo:$blockActivityId"
    }

    override fun onEnqueued() {
        enqueuedCount++
    }
}
