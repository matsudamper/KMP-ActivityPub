package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.shared.AccountId

internal object NoOpFollowDelivery : FollowDelivery {
    override suspend fun send(accountId: AccountId, actorUri: String, followed: Boolean, followId: String?) = Unit
}
