package net.matsudamper.kmp.activitypub.android.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class EncryptedSessionStore(
    private val readEncrypted: () -> ByteArray?,
    private val writeEncrypted: (ByteArray) -> Unit,
    private val encryptionKey: () -> SecretKey,
    private val resetEncryptionKey: () -> Unit,
) : SessionLocalStore {
    constructor(context: Context) : this(AndroidSessionFile(context))

    private constructor(file: AndroidSessionFile) : this(file::read, file::write, Companion::key, Companion::resetKey)

    override suspend fun read(): SessionState? = withContext(Dispatchers.IO) {
        val encrypted = readEncrypted() ?: return@withContext null
        if (encrypted.size <= IV_SIZE) return@withContext recoverUnreadableSession()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, encrypted.copyOfRange(0, IV_SIZE)))
            val plaintext = cipher.doFinal(encrypted.copyOfRange(IV_SIZE, encrypted.size))
            Json.decodeFromString(SessionState.serializer(), plaintext.decodeToString())
        } catch (_: GeneralSecurityException) {
            recoverUnreadableSession()
        } catch (_: SerializationException) {
            recoverUnreadableSession()
        }
    }

    override suspend fun write(state: SessionState): Unit = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val plaintext = Json.encodeToString(SessionState.serializer(), state).encodeToByteArray()
        writeEncrypted(cipher.iv + cipher.doFinal(plaintext))
    }

    private suspend fun recoverUnreadableSession(): SessionState {
        val cleared = SessionState(session = null, backupPending = true)
        resetEncryptionKey()
        // ファイルを無くすと次回起動で古いBlock Storeのセッションを復元してしまう。
        write(cleared)
        return cleared
    }

    companion object {
        private const val KEY_ALIAS = "user-session-v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12

        private fun key(): SecretKey {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            val existing = keyStore.getKey(KEY_ALIAS, null)
            if (existing is SecretKey) return existing
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            return generator.generateKey()
        }

        private fun resetKey() {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }
}

private class AndroidSessionFile(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "user-session"))

    fun read(): ByteArray? = try {
        file.readFully()
    } catch (error: FileNotFoundException) {
        if (file.baseFile.exists() || File("${file.baseFile.path}.bak").exists()) throw error
        null
    }

    fun write(encrypted: ByteArray) {
        val output = file.startWrite()
        try {
            output.write(encrypted)
            output.fd.sync()
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
        file.finishWrite(output)
        if (!file.readFully().contentEquals(encrypted)) throw IOException("セッションの保存を完了できない")
    }
}
