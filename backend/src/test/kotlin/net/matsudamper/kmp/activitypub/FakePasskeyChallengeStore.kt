package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.session.PasskeyChallengePurpose
import net.matsudamper.kmp.activitypub.session.PasskeyChallengeStore
import net.matsudamper.kmp.activitypub.shared.AccountId

internal class FakePasskeyChallengeStore : PasskeyChallengeStore {
    private val rows = mutableMapOf<String, Pair<PasskeyChallengePurpose, AccountId?>>()

    override fun add(
        challenge: String,
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
        expiresAt: Instant,
    ) {
        rows[challenge] = purpose to accountId
    }

    override fun consume(
        challenge: String,
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
    ): Boolean = rows.remove(challenge) == (purpose to accountId)

    override fun close() = Unit
}
