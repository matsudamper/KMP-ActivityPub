package net.matsudamper.kmp.activitypub.android

import android.content.Context
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.BlockstoreClient
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.android.gms.tasks.Tasks
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Block Store に Cookie スナップショットを載せる（端末変更時の復元向け）
 */
internal class CookieBlockStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val blockstore: BlockstoreClient = Blockstore.getClient(appContext)
    private val legacyPreferences =
        appContext.getSharedPreferences(LEGACY_COOKIE_PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): Map<String, Set<String>> {
        val fromBlockStore = readFromBlockStore()
        if (fromBlockStore.isNotEmpty()) {
            return fromBlockStore
        }
        return readLegacyPreferences().also { legacy ->
            if (legacy.isNotEmpty()) {
                writeAsync(legacy)
                legacyPreferences.edit().clear().apply()
            }
        }
    }

    fun writeAsync(byHost: Map<String, Set<String>>) {
        val bytes = encode(byHost)
        if (bytes.size > MAX_ENTRY_BYTES) {
            return
        }

        blockstore
            .isEndToEndEncryptionAvailable
            .continueWithTask { encryptionTask ->
                val storeRequest =
                    StoreBytesData
                        .Builder()
                        .setBytes(bytes)
                        .setKeys(listOf(BLOCK_STORE_KEY))
                        .apply {
                            if (encryptionTask.result == true) {
                                setShouldBackupToCloud(true)
                            }
                        }.build()
                blockstore.storeBytes(storeRequest)
            }
    }

    private fun readFromBlockStore(): Map<String, Set<String>> {
        val retrieveRequest =
            RetrieveBytesRequest
                .Builder()
                .setKeys(listOf(BLOCK_STORE_KEY))
                .build()

        return try {
            val response = Tasks.await(blockstore.retrieveBytes(retrieveRequest), BLOCK_STORE_TIMEOUT)
            val bytes = response.blockstoreDataMap[BLOCK_STORE_KEY]?.bytes
            if (bytes == null) {
                emptyMap()
            } else {
                decode(bytes)
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun readLegacyPreferences(): Map<String, Set<String>> =
        legacyPreferences.all
            .mapNotNull { (host, value) ->
                if (host is String && value is Set<*>) {
                    host to value.filterIsInstance<String>().toSet()
                } else {
                    null
                }
            }.toMap()

    private fun encode(byHost: Map<String, Set<String>>): ByteArray =
        Json.encodeToString(StoredCookies.serializer(), StoredCookies(byHost)).encodeToByteArray()

    private fun decode(bytes: ByteArray): Map<String, Set<String>> =
        try {
            Json.decodeFromString(StoredCookies.serializer(), bytes.decodeToString()).byHost
        } catch (_: Exception) {
            emptyMap()
        }

    @Serializable
    private data class StoredCookies(
        val byHost: Map<String, Set<String>>,
    )

    companion object {
        private const val BLOCK_STORE_KEY = "net.matsudamper.kmp.activitypub.http_cookies"
        private const val LEGACY_COOKIE_PREFERENCES_NAME = "cookies"
        private const val MAX_ENTRY_BYTES = 4 * 1024
        private val BLOCK_STORE_TIMEOUT = TimeUnit.SECONDS.toMillis(10)
    }
}
