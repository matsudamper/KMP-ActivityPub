package net.matsudamper.kmp.activitypub.session

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration
import net.matsudamper.kmp.activitypub.FakeUserSessionStore
import net.matsudamper.kmp.activitypub.shared.AccountId

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class UserSessionServiceTest {
    private val clock = AdvancingClock(Instant.parse("2026-09-01T00:00:00Z"))
    private val service = UserSessionService(sessionStore = FakeUserSessionStore(), ttl = 30.days, clock = clock)
    private val accountId = AccountId(1)

    @Test
    fun `使い続けると 30 日を過ぎても切れない`() {
        val token = service.create(accountId)
        repeat(3) {
            clock.advance(20.days)
            assertEquals(accountId, service.authenticate(token = token, extendable = true)?.accountId)
        }
    }

    @Test
    fun `使わずに 30 日経つと切れる`() {
        val token = service.create(accountId)
        clock.advance(30.days)
        assertNull(service.authenticate(token = token, extendable = true))
    }

    @Test
    fun `延ばすのは 1 日に 1 回まで`() {
        val token = service.create(accountId)
        clock.advance(23.hours)
        assertFalse(service.authenticate(token = token, extendable = true)?.extended ?: true)
        clock.advance(1.hours)
        assertTrue(service.authenticate(token = token, extendable = true)?.extended ?: false)
    }

    @Test
    fun `延ばせないときは期限を変えない`() {
        val token = service.create(accountId)
        clock.advance(20.days)
        assertFalse(service.authenticate(token = token, extendable = false)?.extended ?: true)
        clock.advance(10.days)
        assertNull(service.authenticate(token = token, extendable = true))
    }

    private class AdvancingClock(
        private var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration.toJavaDuration())
        }
    }
}
