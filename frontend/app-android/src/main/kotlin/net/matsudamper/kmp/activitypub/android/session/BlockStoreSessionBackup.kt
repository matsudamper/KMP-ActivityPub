package net.matsudamper.kmp.activitypub.android.session

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.BlockstoreClient
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData

internal class BlockStoreSessionBackup(context: Context) : SessionBackupStore {
    private val client = Blockstore.getClient(context.applicationContext)

    override suspend fun restore(): SessionCookie? {
        val request = RetrieveBytesRequest.Builder().setKeys(listOf(KEY)).build()
        val bytes = client.retrieveBytes(request).await().blockstoreDataMap[KEY]?.bytes ?: return null
        return Json.decodeFromString(SessionCookie.serializer(), bytes.decodeToString())
    }

    override suspend fun save(session: SessionCookie?) {
        if (session == null) {
            client.deleteBytes(DeleteBytesRequest.Builder().setKeys(listOf(KEY)).build()).await()
            return
        }
        val bytes = Json.encodeToString(SessionCookie.serializer(), session).encodeToByteArray()
        require(bytes.size + KEY.encodeToByteArray().size <= BlockstoreClient.MAX_SIZE) { "セッションがBlock Storeの上限を超えている" }
        val backupToCloud = client.isEndToEndEncryptionAvailable.await()
        val request = StoreBytesData.Builder()
            .setKey(KEY)
            .setBytes(bytes)
            .setShouldBackupToCloud(backupToCloud)
            .build()
        if (client.storeBytes(request).await() != bytes.size) throw IOException("セッションのバックアップを完了できない")
    }

    companion object {
        private const val KEY = "user_session.v1"
    }
}
