package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

interface FollowingRepository {
    fun contains(accountId: AccountId, actorUri: String): Boolean
    fun add(accountId: AccountId, actorUri: String, createdAt: Instant): Boolean
    fun remove(accountId: AccountId, actorUri: String): Boolean
}
