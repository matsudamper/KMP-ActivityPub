package net.matsudamper.kmp.activitypub.android.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl

internal class SessionRepository(
    private val local: SessionLocalStore,
    private val backup: SessionBackupStore,
    scope: CoroutineScope,
    private val reportBackupFailure: () -> Unit,
) {
    private val mutex = Mutex()
    private val backupRequests = Channel<Unit>(Channel.CONFLATED)
    private var state: SessionState? = null
    private var revision = 0L

    init {
        scope.launch {
            for (request in backupRequests) {
                try {
                    synchronizeBackup()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    reportBackupFailure()
                }
            }
        }
    }

    suspend fun forRequest(url: HttpUrl, now: Long): RequestSession = mutex.withLock {
        val current = initialize()
        val stored = current?.session
        if (stored != null && (stored.parse()?.expiresAt ?: 0) <= now) {
            replace(null)
            return@withLock RequestSession(cookieHeader = null, revision = revision)
        }
        val cookie = stored?.forRequest(url, now)
        RequestSession(cookieHeader = cookie?.let { "${it.name}=${it.value}" }, revision = revision)
    }

    suspend fun receive(expectedRevision: Long, session: SessionCookie?, now: Long) = mutex.withLock {
        if (expectedRevision != revision) return@withLock
        val next = session?.takeIf { (it.parse()?.expiresAt ?: 0) > now }
        if (state?.session != next || state == null) replace(next)
    }

    private suspend fun initialize(): SessionState? {
        val cached = state
        if (cached != null) return cached
        val saved = local.read()
        if (saved != null) {
            state = saved
            if (saved.backupPending) backupRequests.trySend(Unit)
            return saved
        }
        val restored = try {
            withTimeout(10_000) { backup.restore() }
        } catch (error: CancellationException) {
            // Block Storeのタイムアウトは復元失敗として扱い、呼び出し元のキャンセルは伝播する。
            currentCoroutineContext().ensureActive()
            if (error !is TimeoutCancellationException) throw error
            reportBackupFailure()
            return null
        } catch (_: Exception) {
            reportBackupFailure()
            return null
        }
        val restoredState = SessionState(session = restored, backupPending = false)
        local.write(restoredState)
        state = restoredState
        return restoredState
    }

    private suspend fun replace(session: SessionCookie?) {
        val next = SessionState(session = session, backupPending = true)
        withContext(NonCancellable) {
            local.write(next)
            state = next
            revision++
            backupRequests.trySend(Unit)
        }
    }

    private suspend fun synchronizeBackup() {
        val snapshot = mutex.withLock { state?.takeIf { it.backupPending } } ?: return
        backup.save(snapshot.session)
        mutex.withLock {
            if (state == snapshot) {
                val completed = snapshot.copy(backupPending = false)
                local.write(completed)
                state = completed
            }
        }
    }

    data class RequestSession(
        val cookieHeader: String?,
        val revision: Long,
    )
}
