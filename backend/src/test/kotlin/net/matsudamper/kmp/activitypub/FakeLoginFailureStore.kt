package net.matsudamper.kmp.activitypub

import kotlin.time.Duration
import net.matsudamper.kmp.activitypub.session.LoginFailureStore

internal class FakeLoginFailureStore : LoginFailureStore {
    private val counts = mutableMapOf<String, MutableMap<String, Long>>()

    override fun totalCount(clientIp: String): Long = counts[clientIp]?.values?.sum() ?: 0

    override fun increment(
        clientIp: String,
        username: String,
        ttl: Duration,
    ) {
        val countsOfIp = counts.getOrPut(clientIp) { mutableMapOf() }
        countsOfIp[username] = (countsOfIp[username] ?: 0) + 1
    }

    override fun extend(
        clientIp: String,
        ttl: Duration,
    ) = Unit

    override fun clear(
        clientIp: String,
        username: String,
    ) {
        counts[clientIp]?.remove(username)
    }

    override fun close() = Unit
}
