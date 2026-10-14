package net.matsudamper.kmp.activitypub.android

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.BlockstoreClient
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.android.gms.tasks.Tasks

/**
 * セッション Cookie を Block Store の 1 キーに置く
 */
internal class CookieBlockStore(
    context: Context,
) {
    private val blockstore: BlockstoreClient = Blockstore.getClient(context.applicationContext)

    fun readSession(): StoredCookieEntry? {
        val request =
            RetrieveBytesRequest
                .Builder()
                .setKeys(listOf(KEY))
                .build()
        return try {
            val response =
                Tasks.await(
                    blockstore.retrieveBytes(request),
                    TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                )
            val bytes = response.getBlockstoreDataMap()[KEY]?.bytes ?: return null
            Json.decodeFromString<StoredCookieEntry>(bytes.decodeToString())
        } catch (_: Exception) {
            null
        }
    }

    fun writeSessionAsync(entry: StoredCookieEntry?) {
        if (entry == null) {
            val request =
                DeleteBytesRequest
                    .Builder()
                    .setKeys(listOf(KEY))
                    .build()
            blockstore.deleteBytes(request)
            return
        }

        val bytes = Json.encodeToString(StoredCookieEntry.serializer(), entry).encodeToByteArray()
        if (KEY.encodeToByteArray().size + bytes.size > BlockstoreClient.MAX_SIZE) {
            return
        }

        blockstore.isEndToEndEncryptionAvailable.addOnSuccessListener { encryptionAvailable ->
            val builder =
                StoreBytesData
                    .Builder()
                    .setBytes(bytes)
                    .setKey(KEY)
            if (encryptionAvailable == true) {
                builder.setShouldBackupToCloud(true)
            }
            blockstore.storeBytes(builder.build())
        }
    }

    private companion object {
        const val KEY = "kmp.user_session"
        const val TIMEOUT_SECONDS = 10L
    }
}
