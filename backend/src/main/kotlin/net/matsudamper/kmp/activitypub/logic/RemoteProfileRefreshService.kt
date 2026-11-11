package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository

/**
 * 保存している他のサーバーのアクターを、相手のサーバーから全部取り直す。
 *
 * 相手のサーバーに負荷を掛けないよう、1 件ずつ順に取りに行き、同じサーバーへは [sameHostInterval] を空ける。
 * 同じサーバーで [maxConsecutiveFailuresPerHost] 回続けて失敗したら、そのサーバーは止まっているとみなし、残りは取りに行かない
 */
class RemoteProfileRefreshService(
    private val remoteActors: RemoteActorRepository,
    private val users: RemoteUserLookup,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val sameHostInterval: Duration,
    private val requestTimeout: Duration,
    private val maxConsecutiveFailuresPerHost: Int,
) {
    private val progressFlow = MutableStateFlow<RemoteProfileRefreshProgress?>(null)

    /**
     * 一度も始めていなければ null
     */
    val progress: StateFlow<RemoteProfileRefreshProgress?> = progressFlow.asStateFlow()

    fun start(): StartRemoteProfileRefreshResult {
        val initial = RemoteProfileRefreshProgress(
            running = true,
            total = remoteActors.count(),
            refreshed = 0,
            failed = 0,
            skipped = 0,
            startedAt = clock.instant(),
            finishedAt = null,
        )
        val previous = progressFlow.getAndUpdate { current -> if (current?.running == true) current else initial }
        if (previous?.running == true) return StartRemoteProfileRefreshResult.AlreadyRunning
        scope.launch {
            try {
                refreshAll()
            } finally {
                progressFlow.update { it?.copy(running = false, finishedAt = clock.instant()) }
            }
        }
        return StartRemoteProfileRefreshResult.Started
    }

    private suspend fun refreshAll() {
        val hosts = mutableMapOf<String, HostState>()
        var after: String? = null
        while (true) {
            val actorUris = remoteActors.listActorUris(after = after, limit = PAGE_SIZE)
            if (actorUris.isEmpty()) return
            for (actorUri in actorUris) {
                val host = runCatching { URI(actorUri).host }.getOrNull()?.lowercase()
                if (host == null) {
                    progressFlow.update { it?.copy(skipped = it.skipped + 1) }
                    continue
                }
                val hostState = hosts.getOrPut(host) { HostState() }
                if (hostState.consecutiveFailures >= maxConsecutiveFailuresPerHost) {
                    progressFlow.update { it?.copy(skipped = it.skipped + 1) }
                    continue
                }
                hostState.waitForTurn()
                val refreshed = refresh(actorUri)
                hostState.lastRequestAt = TimeSource.Monotonic.markNow()
                if (refreshed) {
                    hostState.consecutiveFailures = 0
                    progressFlow.update { it?.copy(refreshed = it.refreshed + 1) }
                } else {
                    hostState.consecutiveFailures++
                    progressFlow.update { it?.copy(failed = it.failed + 1) }
                }
            }
            after = actorUris.last()
        }
    }

    private suspend fun refresh(actorUri: String): Boolean {
        return try {
            withTimeoutOrNull(requestTimeout) { users.findByUrl(actorUri) } != null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private inner class HostState {
        var lastRequestAt: TimeSource.Monotonic.ValueTimeMark? = null
        var consecutiveFailures: Int = 0

        suspend fun waitForTurn() {
            val elapsed = lastRequestAt?.elapsedNow() ?: return
            if (elapsed < sameHostInterval) delay(sameHostInterval - elapsed)
        }
    }

    companion object {
        private const val PAGE_SIZE = 100

        val DEFAULT_SAME_HOST_INTERVAL: Duration = 1.seconds
        val DEFAULT_REQUEST_TIMEOUT: Duration = 30.seconds
        const val DEFAULT_MAX_CONSECUTIVE_FAILURES_PER_HOST: Int = 3
    }
}

/**
 * @param total 始めたときに保存していたアクターの数
 * @param failed 取りに行ったが取れなかった数
 * @param skipped 相手のサーバーが続けて失敗したので取りに行かなかった数
 * @param finishedAt 終わっていなければ null
 */
data class RemoteProfileRefreshProgress(
    val running: Boolean,
    val total: Long,
    val refreshed: Long,
    val failed: Long,
    val skipped: Long,
    val startedAt: Instant,
    val finishedAt: Instant?,
)

sealed interface StartRemoteProfileRefreshResult {
    data object Started : StartRemoteProfileRefreshResult

    data object AlreadyRunning : StartRemoteProfileRefreshResult
}
