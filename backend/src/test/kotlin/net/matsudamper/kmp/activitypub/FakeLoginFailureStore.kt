package net.matsudamper.kmp.activitypub

import kotlin.time.Duration
import net.matsudamper.kmp.activitypub.session.LoginFailureStore

internal class FakeLoginFailureStore : LoginFailureStore {
    private val counts = mutableMapOf<String, Long>()

    override fun count(clientIp: String): Long = counts[clientIp] ?: 0

    override fun increment(
        clientIp: String,
        ttl: Duration,
    ) {
        counts[clientIp] = count(clientIp) + 1
    }

    override fun clear(clientIp: String) {
        counts.remove(clientIp)
    }

    override fun close() = Unit
}
