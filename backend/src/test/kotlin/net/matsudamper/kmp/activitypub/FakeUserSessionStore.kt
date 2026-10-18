package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.session.UserSessionStore
import net.matsudamper.kmp.activitypub.shared.AccountId

internal class FakeUserSessionStore : UserSessionStore {
    private val rows = mutableMapOf<String, Pair<AccountId, Instant>>()

    override fun create(
        tokenHash: String,
        accountId: AccountId,
        createdAt: Instant,
        expiresAt: Instant,
    ) {
        rows[tokenHash] = accountId to expiresAt
    }

    override fun findAccountId(
        tokenHash: String,
        now: Instant,
    ): AccountId? = rows[tokenHash]?.takeIf { (_, expiresAt) -> expiresAt > now }?.first

    override fun delete(tokenHash: String) {
        rows.remove(tokenHash)
    }

    override fun close() = Unit
}
