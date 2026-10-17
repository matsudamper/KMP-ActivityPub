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
        repeat(4) { throttle.recordFailure(clientIp = "192.0.2.1", username = "alice") }
        assertFalse(throttle.isLocked("192.0.2.1"))
    }

    @Test
    fun `ユーザー名を変えても 5 回失敗するとその IP を締め出す`() {
        val throttle = throttle()
        listOf("a", "b", "c", "d", "e").forEach { throttle.recordFailure(clientIp = "192.0.2.1", username = it) }
        assertTrue(throttle.isLocked("192.0.2.1"))
        assertFalse(throttle.isLocked("192.0.2.2"))
    }

    @Test
    fun `成功するとそのユーザー名への失敗だけを消す`() {
        val throttle = throttle()
        repeat(3) { throttle.recordFailure(clientIp = "192.0.2.1", username = "alice") }
        repeat(2) { throttle.recordFailure(clientIp = "192.0.2.1", username = "bob") }
        throttle.recordSuccess(clientIp = "192.0.2.1", username = "bob")
        throttle.recordFailure(clientIp = "192.0.2.1", username = "carol")
        assertFalse(throttle.isLocked("192.0.2.1"))
        throttle.recordFailure(clientIp = "192.0.2.1", username = "carol")
        assertTrue(throttle.isLocked("192.0.2.1"))
    }
}
