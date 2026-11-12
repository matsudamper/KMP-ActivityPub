package net.matsudamper.kmp.activitypub.android.timeline

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import com.google.protobuf.InvalidProtocolBufferException
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.ColumnWidthStore
import org.koin.core.annotation.Singleton

@Singleton
internal class AndroidColumnWidthStore(
    context: Context,
) : ColumnWidthStore {
    private val dataStore: DataStore<ColumnWidthSettingProto> = context.columnWidthSettingDataStore

    override val widthsFlow: Flow<Map<String, Float>> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(ColumnWidthSettingProto.getDefaultInstance()) else throw e
        }
        .map { it.widthsMap }

    override suspend fun save(widths: Map<String, Float>): Boolean =
        try {
            dataStore.updateData {
                ColumnWidthSettingProto.newBuilder()
                    .putAllWidths(widths)
                    .build()
            }
            true
        } catch (_: IOException) {
            false
        }
}

private val Context.columnWidthSettingDataStore: DataStore<ColumnWidthSettingProto> by dataStore(
    fileName = "column_width_setting.pb",
    serializer = ColumnWidthSettingSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { ColumnWidthSettingProto.getDefaultInstance() },
)

private object ColumnWidthSettingSerializer : Serializer<ColumnWidthSettingProto> {
    override val defaultValue: ColumnWidthSettingProto = ColumnWidthSettingProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ColumnWidthSettingProto {
        return try {
            ColumnWidthSettingProto.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("ColumnWidthSetting を読めない", e)
        }
    }

    override suspend fun writeTo(t: ColumnWidthSettingProto, output: OutputStream) {
        t.writeTo(output)
    }
}
