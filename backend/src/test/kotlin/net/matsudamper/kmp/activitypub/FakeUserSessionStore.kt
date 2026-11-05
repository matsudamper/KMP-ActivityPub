package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.session.StoredUserSession
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

    override fun find(
        tokenHash: String,
        now: Instant,
    ): StoredUserSession? =
        rows[tokenHash]
            ?.takeIf { (_, expiresAt) -> expiresAt > now }
            ?.let { (accountId, expiresAt) -> StoredUserSession(accountId = accountId, expiresAt = expiresAt) }

    override fun extend(
        tokenHash: String,
        expiresAt: Instant,
    ): Boolean {
        val (accountId, _) = rows[tokenHash] ?: return false
        rows[tokenHash] = accountId to expiresAt
        return true
    }

    override fun delete(tokenHash: String) {
        rows.remove(tokenHash)
    }

    override fun close() = Unit
}
