package net.matsudamper.kmp.activitypub.android.session

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {
    private val url = "https://example.com/graphql".toHttpUrl()
    private val session = SessionCookie(origin = "https://example.com/", cookie = "user_session=token; Path=/; Secure; HttpOnly")

    @Test
    fun `通常起動はローカルを優先してBlock Storeを読まない`() = runTest {
        val local = FakeLocal(SessionState(session, false))
        val backup = FakeBackup()
        val repository = repository(local, backup)

        assertEquals("user_session=token", repository.forRequest(url, 0).cookieHeader)
        assertEquals(0, backup.restores)
    }

    @Test
    fun `新端末ではBlock Storeから復元してローカルに保存する`() = runTest {
        val local = FakeLocal(null)
        val backup = FakeBackup()
        backup.stored = session
        val repository = repository(local, backup)

        assertEquals("user_session=token", repository.forRequest(url, 0).cookieHeader)
        assertEquals(SessionState(session, false), local.stored)
        repository.forRequest(url, 0)
        assertEquals(1, backup.restores)
    }

    @Test
    fun `復元失敗を空データとして確定せず次回通信で再試行する`() = runTest {
        val local = FakeLocal(null)
        val backup = FakeBackup()
        backup.failRestore = true
        val repository = repository(local, backup)

        assertNull(repository.forRequest(url, 0).cookieHeader)
        assertNull(local.stored)
        backup.failRestore = false
        backup.stored = session
        assertEquals("user_session=token", repository.forRequest(url, 0).cookieHeader)
        assertEquals(2, backup.restores)
    }

    @Test
    fun `バックアップ失敗でもセッションはローカルに残り次回起動で再試行する`() = runTest {
        val local = FakeLocal(SessionState(null, false))
        val backup = FakeBackup()
        backup.failSave = true
        val first = repository(local, backup)

        first.receive(first.forRequest(url, 0).revision, session, 0)
        runCurrent()
        assertEquals(SessionState(session, true), local.stored)
        assertEquals("user_session=token", first.forRequest(url, 0).cookieHeader)

        backup.failSave = false
        val restarted = repository(local, backup)
        restarted.forRequest(url, 0)
        runCurrent()
        assertEquals(session, backup.stored)
        assertFalse(local.stored?.backupPending ?: true)
    }

    @Test
    fun `失効時の削除が失敗しても再起動で古いセッションを復元しない`() = runTest {
        val local = FakeLocal(SessionState(session, false))
        val backup = FakeBackup()
        backup.stored = session
        backup.failSave = true
        val first = repository(local, backup)

        first.receive(first.forRequest(url, 0).revision, null, 0)
        runCurrent()
        val restarted = repository(local, backup)
        assertNull(restarted.forRequest(url, 0).cookieHeader)
        assertEquals(0, backup.restores)
        assertEquals(SessionState(null, true), local.stored)

        backup.failSave = false
        runCurrent()
        assertNull(backup.stored)
    }

    @Test
    fun `古い通信の失効応答で新しいセッションを消さない`() = runTest {
        val local = FakeLocal(SessionState(session, false))
        val repository = repository(local, FakeBackup())
        val previous = repository.forRequest(url, 0)
        val updated = session.copy(cookie = "user_session=new; Path=/")

        repository.receive(previous.revision, updated, 0)
        repository.receive(previous.revision, null, 0)
        assertEquals("user_session=new", repository.forRequest(url, 0).cookieHeader)
    }

    @Test
    fun `保存中に新しいセッションが来ても古い保存完了で上書きしない`() = runTest {
        val local = FakeLocal(SessionState(null, false))
        val backup = FakeBackup()
        val gate = CompletableDeferred<Unit>()
        backup.saveGate = gate
        val repository = repository(local, backup)
        repository.receive(repository.forRequest(url, 0).revision, session, 0)
        runCurrent()
        val updated = session.copy(cookie = "user_session=new; Path=/")
        repository.receive(repository.forRequest(url, 0).revision, updated, 0)
        gate.complete(Unit)
        runCurrent()

        assertEquals(updated, local.stored?.session)
        assertEquals(updated, backup.stored)
        assertFalse(local.stored?.backupPending ?: true)
        assertEquals(listOf<SessionCookie?>(session, updated), backup.saves)
    }

    @Test
    fun `期限切れセッションを消してBlock Storeにも削除を反映する`() = runTest {
        val expired = session.copy(cookie = "user_session=token; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
        val local = FakeLocal(SessionState(expired, false))
        val backup = FakeBackup()
        backup.stored = expired
        val repository = repository(local, backup)

        assertNull(repository.forRequest(url, System.currentTimeMillis()).cookieHeader)
        runCurrent()
        assertNull(backup.stored)
        assertEquals(SessionState(null, false), local.stored)
    }

    @Test
    fun `ローカル保存失敗を成功扱いせずバックアップも更新しない`() = runTest {
        val local = FakeLocal(SessionState(null, false))
        val backup = FakeBackup()
        val repository = repository(local, backup)
        val request = repository.forRequest(url, 0)
        local.failWrite = true

        assertFailsWith<IOException> { repository.receive(request.revision, session, 0) }
        runCurrent()
        assertNull(local.stored?.session)
        assertTrue(backup.saves.isEmpty())
    }

    private fun TestScope.repository(local: FakeLocal, backup: FakeBackup): SessionRepository =
        SessionRepository(local, backup, backgroundScope, reportBackupFailure = {})

    private class FakeLocal(var stored: SessionState?) : SessionLocalStore {
        var failWrite = false

        override suspend fun read(): SessionState? = stored

        override suspend fun write(state: SessionState) {
            if (failWrite) throw IOException("保存失敗")
            stored = state
        }
    }

    private class FakeBackup : SessionBackupStore {
        var stored: SessionCookie? = null
        var restores = 0
        var failRestore = false
        var failSave = false
        var saveGate: CompletableDeferred<Unit>? = null
        val saves = mutableListOf<SessionCookie?>()

        override suspend fun restore(): SessionCookie? {
            restores++
            if (failRestore) throw IOException("復元失敗")
            return stored
        }

        override suspend fun save(session: SessionCookie?) {
            saveGate?.await()
            if (failSave) throw IOException("保存失敗")
            saves.add(session)
            stored = session
        }
    }
}
