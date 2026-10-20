package net.matsudamper.kmp.activitypub.delivery

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class DeliveryRetryPolicyTest {
    private val policy = DeliveryRetryPolicy(initialDelay = 30.seconds, maxDelay = 24.hours, giveUpAfter = 7.days)

    @Test
    fun `失敗するたびに間を 2 倍ずつ空ける`() {
        assertEquals(NOW.plusSeconds(30), policy.nextAttemptAt(attempts = 1, enqueuedAt = NOW, now = NOW))
        assertEquals(NOW.plusSeconds(60), policy.nextAttemptAt(attempts = 2, enqueuedAt = NOW, now = NOW))
        assertEquals(NOW.plusSeconds(120), policy.nextAttemptAt(attempts = 3, enqueuedAt = NOW, now = NOW))
    }

    @Test
    fun `間は上限で止まる`() {
        val now = NOW.plusSeconds(60)

        assertEquals(now.plusSeconds(24 * 60 * 60), policy.nextAttemptAt(attempts = 100, enqueuedAt = NOW, now = now))
    }

    @Test
    fun `投函から期限を過ぎるなら諦める`() {
        val now = NOW.plusSeconds(7 * 24 * 60 * 60 - 10)

        assertNull(policy.nextAttemptAt(attempts = 1, enqueuedAt = NOW, now = now))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-01T00:00:00Z")
    }
}
