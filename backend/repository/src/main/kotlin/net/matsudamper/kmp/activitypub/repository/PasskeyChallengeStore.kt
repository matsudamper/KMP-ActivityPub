package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

interface PasskeyChallengeStore {
    /**
     * @param accountId 登録ではログイン中のアカウント。ログインでは null
     */
    fun create(
        challenge: String,
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
        createdAt: Instant,
        expiresAt: Instant,
    )

    /**
     * 期限内で [purpose] と [accountId] も一致すれば消して true。一致しなければ消さない
     */
    fun consume(
        challenge: String,
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
        now: Instant,
    ): Boolean
}

/**
 * passkey_challenges.purpose に [dbValue] を入れる
 */
enum class PasskeyChallengePurpose(
    val dbValue: String,
) {
    Registration("registration"),
    Authentication("authentication"),
}
