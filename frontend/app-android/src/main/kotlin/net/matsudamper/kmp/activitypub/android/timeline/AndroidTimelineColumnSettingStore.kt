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
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.DEFAULT_TIMELINE_COLUMNS
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineColumnSetting
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineColumnSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineSource
import org.koin.core.annotation.Singleton

@Singleton
internal class AndroidTimelineColumnSettingStore(
    context: Context,
) : TimelineColumnSettingStore {
    private val dataStore: DataStore<TimelineColumnSettingProto> = context.timelineColumnSettingDataStore

    override val columnsFlow: Flow<List<TimelineColumnSetting>> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(TimelineColumnSettingProto.getDefaultInstance()) else throw e
        }
        .map { setting ->
            if (setting.saved) {
                setting.columnsList.map { it.toTimelineColumnSetting() }
            } else {
                DEFAULT_TIMELINE_COLUMNS
            }
        }

    override suspend fun save(columns: List<TimelineColumnSetting>): Boolean =
        try {
            dataStore.updateData {
                TimelineColumnSettingProto.newBuilder()
                    .setSaved(true)
                    .addAllColumns(columns.map { it.toProto() })
                    .build()
            }
            true
        } catch (_: IOException) {
            false
        }
}

private val Context.timelineColumnSettingDataStore: DataStore<TimelineColumnSettingProto> by dataStore(
    fileName = "timeline_column_setting.pb",
    serializer = TimelineColumnSettingSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { TimelineColumnSettingProto.getDefaultInstance() },
)

private fun TimelineColumnProto.toTimelineColumnSetting(): TimelineColumnSetting =
    TimelineColumnSetting(
        id = id,
        name = name,
        sources = buildSet {
            if (home) add(TimelineSource.Home)
            followingGroupIdsList.forEach { add(TimelineSource.FollowingGroup(it)) }
        },
    )

private fun TimelineColumnSetting.toProto(): TimelineColumnProto =
    TimelineColumnProto.newBuilder()
        .setId(id)
        .setName(name)
        .setHome(TimelineSource.Home in sources)
        .addAllFollowingGroupIds(sources.filterIsInstance<TimelineSource.FollowingGroup>().map { it.groupId })
        .build()

private object TimelineColumnSettingSerializer : Serializer<TimelineColumnSettingProto> {
    override val defaultValue: TimelineColumnSettingProto = TimelineColumnSettingProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): TimelineColumnSettingProto {
        return try {
            TimelineColumnSettingProto.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("TimelineColumnSetting を読めない", e)
        }
    }

    override suspend fun writeTo(t: TimelineColumnSettingProto, output: OutputStream) {
        t.writeTo(output)
    }
}
