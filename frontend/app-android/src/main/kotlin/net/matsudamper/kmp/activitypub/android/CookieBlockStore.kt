package net.matsudamper.kmp.activitypub.android

import android.content.Context
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.BlockstoreClient
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

    fun read(): Set<String> {
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

    fun writeAsync(cookies: Set<String>) {
        val bytes = encode(cookies)
        if (bytes.size > MAX_ENTRY_BYTES) {
            return
        }

        blockstore
            .isEndToEndEncryptionAvailable
            .continueWithTask { encryptionTask ->
                val storeRequestBuilder =
                    StoreBytesData
                        .Builder()
                        .setBytes(bytes)
                        .setKey(BLOCK_STORE_KEY)
                if (encryptionTask.result == true) {
                    storeRequestBuilder.setShouldBackupToCloud(true)
                }
                val storeRequest = storeRequestBuilder.build()
                blockstore.storeBytes(storeRequest)
            }
    }

    private fun readFromBlockStore(): Set<String> {
        val retrieveRequest =
            RetrieveBytesRequest
                .Builder()
                .setKeys(listOf(BLOCK_STORE_KEY))
                .build()

        return try {
            val response =
                Tasks.await(
                    blockstore.retrieveBytes(retrieveRequest),
                    BLOCK_STORE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                )
            val bytes = response.getBlockstoreDataMap()[BLOCK_STORE_KEY]?.bytes
            if (bytes == null) {
                emptySet()
            } else {
                decode(bytes)
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun readLegacyPreferences(): Set<String> =
        legacyPreferences.all.values
            .filterIsInstance<Set<*>>()
            .flatMap { entry -> entry.filterIsInstance<String>() }
            .toSet()

    private fun encode(cookies: Set<String>): ByteArray =
        Json.encodeToString(StoredCookies.serializer(), StoredCookies(cookies)).encodeToByteArray()

    private fun decode(bytes: ByteArray): Set<String> =
        try {
            val stored = Json.decodeFromString(StoredCookies.serializer(), bytes.decodeToString())
            if (stored.cookies.isNotEmpty()) {
                stored.cookies
            } else {
                stored.byHost.values.flatten().toSet()
            }
        } catch (_: Exception) {
            emptySet()
        }

    @Serializable
    private data class StoredCookies(
        val cookies: Set<String> = emptySet(),
        val byHost: Map<String, Set<String>> = emptyMap(),
    )

    companion object {
        private const val BLOCK_STORE_KEY = "net.matsudamper.kmp.activitypub.http_cookies"
        private const val LEGACY_COOKIE_PREFERENCES_NAME = "cookies"
        private const val MAX_ENTRY_BYTES = 4 * 1024
        private const val BLOCK_STORE_TIMEOUT_SECONDS = 10L
    }
}
