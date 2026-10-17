package net.matsudamper.kmp.activitypub

import kotlin.time.Duration
import net.matsudamper.kmp.activitypub.session.LoginFailureStore

internal class FakeLoginFailureStore : LoginFailureStore {
    private val counts = mutableMapOf<String, MutableMap<String, Long>>()

    override fun reserve(
        clientIp: String,
        username: String,
        maxFailures: Int,
        ttl: Duration,
    ): Boolean {
        val countsOfIp = counts.getOrPut(clientIp) { mutableMapOf() }
        if (countsOfIp.values.sum() >= maxFailures) return false
        countsOfIp[username] = (countsOfIp[username] ?: 0) + 1
        return true
    }

    override fun clear(
        clientIp: String,
        username: String,
    ) {
        counts[clientIp]?.remove(username)
    }

    override fun close() = Unit
}
