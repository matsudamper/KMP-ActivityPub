package net.matsudamper.kmp.activitypub.android.server

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import com.google.protobuf.InvalidProtocolBufferException
import org.koin.core.annotation.Singleton

@Singleton
internal class ServerSettingStore(
    context: Context,
) {
    private val dataStore: DataStore<ServerSetting> = context.serverSettingDataStore

    val serverUrlFlow: Flow<String?> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(ServerSetting.getDefaultInstance()) else throw e
        }
        .map { setting -> setting.serverUrl.ifEmpty { null } }

    suspend fun save(serverUrl: String) {
        dataStore.updateData { setting ->
            setting.toBuilder().setServerUrl(serverUrl).build()
        }
    }
}

private val Context.serverSettingDataStore: DataStore<ServerSetting> by dataStore(
    fileName = "server_setting.pb",
    serializer = ServerSettingSerializer,
)

private object ServerSettingSerializer : Serializer<ServerSetting> {
    override val defaultValue: ServerSetting = ServerSetting.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ServerSetting {
        return try {
            ServerSetting.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("ServerSetting を読めない", e)
        }
    }

    override suspend fun writeTo(t: ServerSetting, output: OutputStream) {
        t.writeTo(output)
    }
}
