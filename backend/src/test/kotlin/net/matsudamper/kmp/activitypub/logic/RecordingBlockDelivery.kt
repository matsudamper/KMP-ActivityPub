package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 相手のサーバーには何も送らず、呼ばれたことだけを残す
 */
internal class RecordingBlockDelivery : BlockDelivery {
    val blockedActors = mutableListOf<String>()
    val rejectedAccounts = mutableListOf<AccountId>()
    val undoneBlockActivityIds = mutableListOf<String>()

    override suspend fun blockActor(accountId: AccountId, actorUri: String) {
        blockedActors += actorUri
    }

    override fun rejectBlockedFollowers(accountId: AccountId) {
        rejectedAccounts += accountId
    }

    override suspend fun undoBlock(accountId: AccountId, actorUri: String, blockActivityId: String) {
        undoneBlockActivityIds += blockActivityId
    }
}
