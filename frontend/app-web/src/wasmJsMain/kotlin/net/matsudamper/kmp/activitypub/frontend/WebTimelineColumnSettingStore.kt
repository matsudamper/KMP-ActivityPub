package net.matsudamper.kmp.activitypub.frontend

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.DEFAULT_TIMELINE_COLUMNS
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineColumnSetting
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineColumnSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineFontSize
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineSource

internal class WebTimelineColumnSettingStore : TimelineColumnSettingStore {
    private val columnsStateFlow = MutableStateFlow(loadColumns())

    override val columnsFlow: Flow<List<TimelineColumnSetting>> = columnsStateFlow.asStateFlow()

    /**
     * ストレージを拒否されたブラウザでも、開いている間だけはカラムを変えられるようにする
     */
    override suspend fun save(columns: List<TimelineColumnSetting>): Boolean {
        val encoded = Json.encodeToString(columns.map { it.toStored() })
        val saved = runCatching { localStorage.setItem(TIMELINE_COLUMNS_KEY, encoded) }.isSuccess
        columnsStateFlow.value = columns
        return saved
    }

    /**
     * ストレージを拒否されると getItem が例外を投げ、生成時に読むのでアプリごと起動しなくなる。
     * 壊れた値も同じく起動を止めないよう、既定のカラムに戻す
     */
    private fun loadColumns(): List<TimelineColumnSetting> {
        val saved = runCatching { localStorage.getItem(TIMELINE_COLUMNS_KEY) }.getOrNull() ?: return DEFAULT_TIMELINE_COLUMNS
        return runCatching { Json.decodeFromString<List<StoredTimelineColumn>>(saved) }
            .map { columns -> columns.map { it.toTimelineColumnSetting() } }
            .getOrDefault(DEFAULT_TIMELINE_COLUMNS)
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class StoredTimelineColumn(
    val id: String,
    val name: String,
    /** メインをホームと呼んでいた頃に保存したカラムも読めるようにする */
    @JsonNames("home")
    val mainGroup: Boolean,
    val followingGroupIds: List<String>,
    // 通知を選べるようになる前に保存した値には無いので、読めずに既定のカラムへ戻さないよう既定値を持つ
    val notifications: Boolean = false,
    // 文字の大きさを選べるようになる前に保存した値には無いので、既定値を持つ
    val fontSize: StoredTimelineFontSize = StoredTimelineFontSize.Medium,
)

@Serializable
private enum class StoredTimelineFontSize {
    Small,
    Medium,
    Large,
}

private fun TimelineColumnSetting.toStored(): StoredTimelineColumn =
    StoredTimelineColumn(
        id = id,
        name = name,
        mainGroup = TimelineSource.MainGroup in sources,
        followingGroupIds = sources.filterIsInstance<TimelineSource.FollowingGroup>().map { it.groupId },
        notifications = TimelineSource.Notifications in sources,
        fontSize = when (fontSize) {
            TimelineFontSize.Small -> StoredTimelineFontSize.Small
            TimelineFontSize.Medium -> StoredTimelineFontSize.Medium
            TimelineFontSize.Large -> StoredTimelineFontSize.Large
        },
    )

private fun StoredTimelineColumn.toTimelineColumnSetting(): TimelineColumnSetting =
    TimelineColumnSetting(
        id = id,
        name = name,
        sources = buildSet {
            if (mainGroup) add(TimelineSource.MainGroup)
            followingGroupIds.forEach { add(TimelineSource.FollowingGroup(it)) }
            if (notifications) add(TimelineSource.Notifications)
        },
        fontSize = when (fontSize) {
            StoredTimelineFontSize.Small -> TimelineFontSize.Small
            StoredTimelineFontSize.Medium -> TimelineFontSize.Medium
            StoredTimelineFontSize.Large -> TimelineFontSize.Large
        },
    )

/**
 * 購読先を 1 つずつ選んでいた頃の "timelineColumns" とは形が違うので、別の鍵に置く
 */
private const val TIMELINE_COLUMNS_KEY = "timelineColumnsV2"
