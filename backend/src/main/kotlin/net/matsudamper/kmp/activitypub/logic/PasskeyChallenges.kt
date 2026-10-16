package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 発行した challenge。使ったら消して、同じものを二度通さない。
 * 有効なのは数分だけなので、再起動で消えてもやり直してもらえばよい
 */
internal class PasskeyChallenges(
    private val clock: Clock,
    private val ttl: Duration,
) {
    /**
     * ログイン前でも発行できるので、送り続けられてもメモリを使い切らないよう、上限を超えたら古いものから捨てる
     */
    private val entries =
        object : LinkedHashMap<String, Entry>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > MAX_ENTRIES
        }

    /**
     * @param accountId 登録ではログイン中のアカウント。ログインでは null
     */
    @Synchronized
    fun add(
        challenge: String,
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
    ) {
        val now = clock.instant()
        // 挿入順なので、期限切れは先頭に固まっている
        val iterator = entries.values.iterator()
        while (iterator.hasNext() && iterator.next().expiresAt <= now) iterator.remove()

        entries[challenge] = Entry(purpose = purpose, accountId = accountId, expiresAt = now.plus(ttl.toJavaDuration()))
    }

    /**
     * 期限内で [purpose] と [accountId] も一致すれば消して true。一致しなければ消さない
     */
    @Synchronized
    fun consume(
        challenge: String,
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
    ): Boolean {
        val entry = entries[challenge] ?: return false
        if (entry.purpose != purpose || entry.accountId != accountId || entry.expiresAt <= clock.instant()) return false
        entries.remove(challenge)
        return true
    }

    private class Entry(
        val purpose: PasskeyChallengePurpose,
        val accountId: AccountId?,
        val expiresAt: Instant,
    )

    private companion object {
        const val MAX_ENTRIES = 10_000
    }
}

internal enum class PasskeyChallengePurpose {
    Registration,
    Authentication,
}
