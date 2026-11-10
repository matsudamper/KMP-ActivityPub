package net.matsudamper.kmp.activitypub.frontend

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.DEFAULT_TIMELINE_COLUMNS
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineColumnSetting
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineColumnSettingStore

internal class WebTimelineColumnSettingStore : TimelineColumnSettingStore {
    private val columnsStateFlow = MutableStateFlow(loadColumns())

    override val columnsFlow: Flow<List<TimelineColumnSetting>> = columnsStateFlow.asStateFlow()

    /**
     * ストレージを拒否されたブラウザでも、開いている間だけはカラムを変えられるようにする
     */
    override suspend fun save(columns: List<TimelineColumnSetting>): Boolean {
        val encoded = columns.joinToString(separator = COLUMN_SEPARATOR) { it.encode() }
        val saved = runCatching { localStorage.setItem(TIMELINE_COLUMNS_KEY, encoded) }.isSuccess
        columnsStateFlow.value = columns
        return saved
    }

    /**
     * ストレージを拒否されると getItem が例外を投げ、生成時に読むのでアプリごと起動しなくなる
     */
    private fun loadColumns(): List<TimelineColumnSetting> {
        val saved = runCatching { localStorage.getItem(TIMELINE_COLUMNS_KEY) }.getOrNull()
        return when {
            saved == null -> DEFAULT_TIMELINE_COLUMNS
            saved.isEmpty() -> listOf()
            else -> saved.split(COLUMN_SEPARATOR).mapNotNull { decodeColumn(it) }
        }
    }
}

private fun TimelineColumnSetting.encode(): String =
    when (this) {
        TimelineColumnSetting.All -> ALL_VALUE
        TimelineColumnSetting.Home -> HOME_VALUE
        is TimelineColumnSetting.FollowingGroup -> FOLLOWING_GROUP_PREFIX + groupId
    }

private fun decodeColumn(value: String): TimelineColumnSetting? =
    when {
        value == ALL_VALUE -> TimelineColumnSetting.All
        value == HOME_VALUE -> TimelineColumnSetting.Home
        value.startsWith(FOLLOWING_GROUP_PREFIX) -> TimelineColumnSetting.FollowingGroup(value.removePrefix(FOLLOWING_GROUP_PREFIX))
        else -> null
    }

private const val TIMELINE_COLUMNS_KEY = "timelineColumns"
private const val COLUMN_SEPARATOR = ","
private const val ALL_VALUE = "all"
private const val HOME_VALUE = "home"
private const val FOLLOWING_GROUP_PREFIX = "group:"
