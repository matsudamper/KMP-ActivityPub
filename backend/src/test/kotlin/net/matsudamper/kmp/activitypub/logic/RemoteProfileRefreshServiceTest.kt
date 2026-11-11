package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.matsudamper.kmp.activitypub.FakeRemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord

class RemoteProfileRefreshServiceTest {
    private val remoteActors = FakeRemoteActorRepository()
    private val requested = mutableListOf<String>()
    private val unreachableHosts = mutableSetOf<String>()
    private val users = object : RemoteUserLookup {
        override suspend fun findByAcct(username: String, host: String): RemoteUser? = error("使わない")

        override suspend fun findByUrl(url: String): RemoteUser? {
            requested.add(url)
            if (unreachableHosts.any { url.startsWith("https://$it/") }) throw IllegalStateException("届かない")
            return RemoteUser(actorUri = url, username = url.substringAfterLast('/'), displayName = null, iconUrl = null, displayNameEmojis = listOf())
        }
    }

    @Test
    fun refreshesEveryStoredActor() {
        saveActors("https://a.example/users/1", "https://b.example/users/2")

        val service = createService()

        assertEquals(StartRemoteProfileRefreshResult.Started, service.start())
        assertEquals(listOf("https://a.example/users/1", "https://b.example/users/2"), requested)
        val progress = checkNotNull(service.progress.value)
        assertEquals(false, progress.running)
        assertEquals(2, progress.total)
        assertEquals(2, progress.refreshed)
        assertEquals(0, progress.failed)
        assertEquals(NOW, progress.finishedAt)
    }

    @Test
    fun stopsRequestingHostAfterConsecutiveFailures() {
        unreachableHosts.add("down.example")
        saveActors(
            "https://down.example/users/1",
            "https://down.example/users/2",
            "https://down.example/users/3",
            "https://down.example/users/4",
            "https://up.example/users/1",
        )

        val service = createService(maxConsecutiveFailuresPerHost = 2)
        service.start()

        assertEquals(listOf("https://down.example/users/1", "https://down.example/users/2", "https://up.example/users/1"), requested)
        val progress = checkNotNull(service.progress.value)
        assertEquals(1, progress.refreshed)
        assertEquals(2, progress.failed)
        assertEquals(2, progress.skipped)
    }

    @Test
    fun startWhileRunningIsRejected() {
        saveActors("https://a.example/users/1", "https://a.example/users/2")
        // 同じサーバーの 2 人目で待つので、1 回目が終わらないうちに 2 回目を始めることになる
        val scope = CoroutineScope(Dispatchers.Default)
        val service = createService(scope = scope, sameHostInterval = 60.seconds)

        assertEquals(StartRemoteProfileRefreshResult.Started, service.start())
        assertEquals(StartRemoteProfileRefreshResult.AlreadyRunning, service.start())
        scope.cancel()
    }

    private fun saveActors(vararg actorUris: String) {
        actorUris.forEach { actorUri ->
            remoteActors.save(
                RemoteActorRecord(actorUri, "$actorUri/inbox", null, "key", RemoteActorProfileRecord(null, null, null, null, null, listOf())),
                NOW,
            )
        }
    }

    private fun createService(
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
        sameHostInterval: Duration = Duration.ZERO,
        maxConsecutiveFailuresPerHost: Int = 3,
    ): RemoteProfileRefreshService =
        RemoteProfileRefreshService(
            remoteActors = remoteActors,
            users = users,
            scope = scope,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
            sameHostInterval = sameHostInterval,
            requestTimeout = 5.seconds,
            maxConsecutiveFailuresPerHost = maxConsecutiveFailuresPerHost,
        )

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-11T00:00:00Z")
    }
}
