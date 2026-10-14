package net.matsudamper.kmp.activitypub.android

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.BlockstoreClient
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

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

    @Volatile
    private var serializedPersist: Task<Void> = Tasks.forResult(null)

    fun readEntries(): List<StoredCookieEntry> {
        val fromBlockStore = readFromBlockStore()
        if (fromBlockStore.isNotEmpty()) {
            return fromBlockStore
        }
        return readLegacyPreferences().also { legacy ->
            if (legacy.isNotEmpty()) {
                writeEntriesAsync(legacy)
                legacyPreferences.edit().clear().apply()
            }
        }
    }

    fun writeEntriesAsync(entries: List<StoredCookieEntry>) {
        val payload = encode(entries)
        val chunks = splitPayload(payload)
        if (payload.isNotEmpty() && chunks.isEmpty()) {
            return
        }

        serializedPersist =
            serializedPersist.continueWithTask {
                persistChunks(chunks)
            }
    }

    private fun persistChunks(chunks: List<ByteArray>): Task<Void> {
        if (chunks.isEmpty()) {
            return deleteStaleChunkKeys(fromIndex = 0)
        }
        return blockstore.isEndToEndEncryptionAvailable.continueWithTask { encryptionTask ->
            val backupToCloud = encryptionTask.result == true
            persistChunkAt(chunks = chunks, backupToCloud = backupToCloud, index = 0).continueWithTask {
                deleteStaleChunkKeys(fromIndex = chunks.size)
            }
        }
    }

    private fun persistChunkAt(
        chunks: List<ByteArray>,
        backupToCloud: Boolean,
        index: Int,
    ): Task<Void> {
        if (index >= chunks.size) {
            return Tasks.forResult(null)
        }
        val key = chunkKey(index)
        val storeRequestBuilder =
            StoreBytesData
                .Builder()
                .setBytes(chunks[index])
                .setKey(key)
        if (backupToCloud) {
            storeRequestBuilder.setShouldBackupToCloud(true)
        }
        return blockstore
            .storeBytes(storeRequestBuilder.build())
            .continueWithTask {
                persistChunkAt(chunks = chunks, backupToCloud = backupToCloud, index = index + 1)
            }
    }

    private fun deleteStaleChunkKeys(fromIndex: Int): Task<Void> {
        val staleKeys =
            (fromIndex until BlockstoreClient.MAX_ENTRY_COUNT).map { index -> chunkKey(index) }
        if (staleKeys.isEmpty()) {
            return Tasks.forResult(null)
        }
        val deleteRequest =
            DeleteBytesRequest
                .Builder()
                .setKeys(staleKeys)
                .build()
        return blockstore.deleteBytes(deleteRequest).continueWith { Tasks.forResult(null) }
    }

    private fun readFromBlockStore(): List<StoredCookieEntry> {
        val keys = (0 until BlockstoreClient.MAX_ENTRY_COUNT).map { index -> chunkKey(index) }
        val retrieveRequest =
            RetrieveBytesRequest
                .Builder()
                .setKeys(keys)
                .build()

        return try {
            val response =
                Tasks.await(
                    blockstore.retrieveBytes(retrieveRequest),
                    BLOCK_STORE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                )
            val payload = readChunkPayload(response)
            if (payload.isEmpty()) {
                emptyList()
            } else {
                decode(payload)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readChunkPayload(
        response: com.google.android.gms.auth.blockstore.RetrieveBytesResponse,
    ): ByteArray {
        val dataMap = response.getBlockstoreDataMap()
        val parts = mutableListOf<ByteArray>()
        for (index in 0 until BlockstoreClient.MAX_ENTRY_COUNT) {
            val bytes = dataMap[chunkKey(index)]?.bytes ?: break
            parts.add(bytes)
        }
        return parts.fold(byteArrayOf()) { acc, part -> acc + part }
    }

    private fun splitPayload(payload: ByteArray): List<ByteArray> {
        if (payload.isEmpty()) {
            return emptyList()
        }
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        var index = 0
        while (offset < payload.size && index < BlockstoreClient.MAX_ENTRY_COUNT) {
            val key = chunkKey(index)
            val chunkCapacity = maxValueBytes(key)
            if (chunkCapacity <= 0) {
                return emptyList()
            }
            val end = minOf(offset + chunkCapacity, payload.size)
            chunks.add(payload.copyOfRange(offset, end))
            offset = end
            index++
        }
        if (offset < payload.size) {
            return emptyList()
        }
        return chunks
    }

    private fun maxValueBytes(key: String): Int = MAX_BLOCK_BYTES - key.encodeToByteArray().size

    private fun chunkKey(index: Int): String = if (index == 0) KEY_PREFIX else "$KEY_PREFIX.$index"

    private fun readLegacyPreferences(): List<StoredCookieEntry> =
        legacyPreferences.all
            .mapNotNull { (host, value) ->
                if (host is String && value is Set<*>) {
                    val origin = "https://$host"
                    value.filterIsInstance<String>().map { cookieValue ->
                        StoredCookieEntry(origin = origin, value = cookieValue)
                    }
                } else {
                    null
                }
            }.flatten()

    private fun encode(entries: List<StoredCookieEntry>): ByteArray =
        Json.encodeToString(StoredCookies.serializer(), StoredCookies(entries)).encodeToByteArray()

    private fun decode(bytes: ByteArray): List<StoredCookieEntry> =
        try {
            val stored = Json.decodeFromString(StoredCookies.serializer(), bytes.decodeToString())
            if (stored.entries.isNotEmpty()) {
                stored.entries
            } else {
                migrateLegacyStored(stored)
            }
        } catch (_: Exception) {
            emptyList()
        }

    private fun migrateLegacyStored(stored: StoredCookies): List<StoredCookieEntry> {
        if (stored.legacyCookies.isNotEmpty()) {
            return emptyList()
        }
        return stored.byHost.flatMap { (host, values) ->
            val origin = "https://$host"
            values.map { value -> StoredCookieEntry(origin = origin, value = value) }
        }
    }

    @Serializable
    private data class StoredCookies(
        val entries: List<StoredCookieEntry> = emptyList(),
        val legacyCookies: Set<String> = emptySet(),
        val byHost: Map<String, Set<String>> = emptyMap(),
    )

    companion object {
        private const val KEY_PREFIX = "kmp.cookies"
        private const val LEGACY_COOKIE_PREFERENCES_NAME = "cookies"
        private const val MAX_BLOCK_BYTES = 1024
        private const val BLOCK_STORE_TIMEOUT_SECONDS = 10L
    }
}
