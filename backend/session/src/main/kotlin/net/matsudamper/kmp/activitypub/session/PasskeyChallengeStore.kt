package net.matsudamper.kmp.activitypub.session

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 発行した challenge。使ったら消して、同じものを二度通さない
 */
interface PasskeyChallengeStore : AutoCloseable {
    /**
     * @param accountId 登録ではログイン中のアカウント。ログインでは null
     */
    fun add(
        challenge: String,
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
        expiresAt: Instant,
    )

    /**
     * 期限内のものを消し、[purpose] と [accountId] も一致していれば true
     */
    fun consume(
        challenge: String,
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
    ): Boolean
}

enum class PasskeyChallengePurpose {
    Registration,
    Authentication,
}
