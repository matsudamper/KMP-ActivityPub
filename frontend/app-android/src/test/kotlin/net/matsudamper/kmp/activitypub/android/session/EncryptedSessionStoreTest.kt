package net.matsudamper.kmp.activitypub.android.session

import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class EncryptedSessionStoreTest {
    private val session = SessionState(SessionCookie("https://example.com/", "user_session=token; Path=/"), false)
    private val cleared = SessionState(session = null, backupPending = true)

    @Test
    fun `鍵を失っても削除状態を保存して再ログインできる`() = runTest {
        val fixture = Fixture()
        fixture.store().write(session)
        fixture.key = newKey()

        assertEquals(cleared, fixture.store().read())
        assertEquals(cleared, fixture.store().read())
        assertNotNull(fixture.encrypted)
        fixture.store().write(session)
        assertEquals(session, fixture.store().read())
    }

    @Test
    fun `暗号データの改変を検知して未ログイン状態に回復する`() = runTest {
        val fixture = Fixture()
        fixture.store().write(session)
        val encrypted = assertNotNull(fixture.encrypted)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()

        assertEquals(cleared, fixture.store().read())
        assertEquals(cleared, fixture.store().read())
    }

    @Test
    fun `短いデータと壊れたJSONから未ログイン状態に回復する`() = runTest {
        val fixture = Fixture()
        fixture.encrypted = byteArrayOf(1, 2)
        assertEquals(cleared, fixture.store().read())

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, fixture.key)
        fixture.encrypted = cipher.iv + cipher.doFinal("broken JSON".encodeToByteArray())
        assertEquals(cleared, fixture.store().read())
        assertEquals(cleared, fixture.store().read())
    }

    @Test
    fun `削除状態を保存できなければ回復成功とは扱わない`() = runTest {
        val fixture = Fixture()
        fixture.encrypted = byteArrayOf(1, 2)
        fixture.failWrite = true

        assertFailsWith<IOException> { fixture.store().read() }
        fixture.failWrite = false
        assertEquals(cleared, fixture.store().read())
    }

    private class Fixture {
        var key = newKey()
        var encrypted: ByteArray? = null
        var failWrite = false

        fun store(): EncryptedSessionStore = EncryptedSessionStore(
            readEncrypted = { encrypted },
            writeEncrypted = {
                if (failWrite) throw IOException("保存失敗")
                encrypted = it
            },
            encryptionKey = { key },
            resetEncryptionKey = { key = newKey() },
        )
    }

    companion object {
        private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").generateKey()
    }
}
