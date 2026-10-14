package net.matsudamper.kmp.activitypub.android.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal class EncryptedSessionStore(context: Context) : SessionLocalStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "user-session"))

    override suspend fun read(): SessionState? = withContext(Dispatchers.IO) {
        val encrypted = try {
            file.readFully()
        } catch (error: FileNotFoundException) {
            if (file.baseFile.exists() || File("${file.baseFile.path}.bak").exists()) throw error
            return@withContext null
        }
        if (encrypted.size <= IV_SIZE) throw IOException("セッションデータを読めない")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(0, IV_SIZE)))
        val plaintext = cipher.doFinal(encrypted.copyOfRange(IV_SIZE, encrypted.size))
        Json.decodeFromString(SessionState.serializer(), plaintext.decodeToString())
    }

    override suspend fun write(state: SessionState): Unit = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plaintext = Json.encodeToString(SessionState.serializer(), state).encodeToByteArray()
        val encrypted = cipher.iv + cipher.doFinal(plaintext)
        val output = file.startWrite()
        try {
            output.write(encrypted)
            output.fd.sync()
            file.finishWrite(output)
            if (!file.readFully().contentEquals(encrypted)) throw IOException("セッションの保存を完了できない")
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

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

    companion object {
        private const val KEY_ALIAS = "user-session-v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
