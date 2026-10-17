package net.matsudamper.kmp.activitypub.session

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import net.matsudamper.kmp.activitypub.FakeLoginFailureStore

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class LoginThrottleTest {
    private fun throttle() = LoginThrottle(failureStore = FakeLoginFailureStore(), maxFailures = 5, lockDuration = 30.days)

    @Test
    fun `4 回の失敗ではまだ締め出さない`() {
        val throttle = throttle()
        repeat(4) { throttle.recordFailure("192.0.2.1") }
        assertFalse(throttle.isLocked("192.0.2.1"))
    }

    @Test
    fun `5 回失敗するとその IP を締め出す`() {
        val throttle = throttle()
        repeat(5) { throttle.recordFailure("192.0.2.1") }
        assertTrue(throttle.isLocked("192.0.2.1"))
        assertFalse(throttle.isLocked("192.0.2.2"))
    }

    @Test
    fun `成功すると失敗の回数を数え直す`() {
        val throttle = throttle()
        repeat(4) { throttle.recordFailure("192.0.2.1") }
        throttle.recordSuccess("192.0.2.1")
        throttle.recordFailure("192.0.2.1")
        assertFalse(throttle.isLocked("192.0.2.1"))
    }
}
