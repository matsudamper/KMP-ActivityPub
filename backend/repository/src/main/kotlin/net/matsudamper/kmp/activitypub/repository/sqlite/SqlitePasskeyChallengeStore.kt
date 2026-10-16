package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.PasskeyChallengePurpose
import net.matsudamper.kmp.activitypub.repository.PasskeyChallengeStore
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.PASSKEY_CHALLENGES
import net.matsudamper.kmp.activitypub.shared.AccountId

internal class SqlitePasskeyChallengeStore(
    private val jooq: SqliteJooq,
) : PasskeyChallengeStore {
    /**
     * 期限切れの行もここで消す。発行したまま使われない challenge は読み直されずに溜まる
     */
    override fun create(
        challenge: String,
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
        createdAt: Instant,
        expiresAt: Instant,
    ) {
        jooq.transaction { dsl ->
            dsl
                .deleteFrom(PASSKEY_CHALLENGES)
                .where(PASSKEY_CHALLENGES.EXPIRES_AT.lt(StoredInstant.format(createdAt)))
                .execute()

            dsl
                .insertInto(PASSKEY_CHALLENGES)
                .set(PASSKEY_CHALLENGES.CHALLENGE, challenge)
                .set(PASSKEY_CHALLENGES.PURPOSE, purpose.dbValue)
                .set(PASSKEY_CHALLENGES.ACCOUNT_ID, accountId?.value)
                .set(PASSKEY_CHALLENGES.EXPIRES_AT, StoredInstant.format(expiresAt))
                .execute()
        }
    }

    override fun consume(
        challenge: String,
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
        now: Instant,
    ): Boolean = jooq.withConnection { dsl ->
        dsl
            .deleteFrom(PASSKEY_CHALLENGES)
            .where(PASSKEY_CHALLENGES.CHALLENGE.eq(challenge))
            .and(PASSKEY_CHALLENGES.PURPOSE.eq(purpose.dbValue))
            .and(PASSKEY_CHALLENGES.ACCOUNT_ID.isNotDistinctFrom(accountId?.value))
            .and(PASSKEY_CHALLENGES.EXPIRES_AT.gt(StoredInstant.format(now)))
            .execute() == 1
    }
}
