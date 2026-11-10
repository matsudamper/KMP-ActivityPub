package net.matsudamper.kmp.activitypub.frontend.logic.timeline

import kotlinx.coroutines.flow.Flow

/**
 * 端末ごとに覚えておく、タイムラインのカラムやタブに並べる購読先。サーバーには送らない
 */
interface TimelineColumnSettingStore {
    /**
     * 一度も保存していなければ [DEFAULT_TIMELINE_COLUMNS]。全部消したときは空
     */
    val columnsFlow: Flow<List<TimelineColumnSetting>>

    /**
     * @return 端末に書き込めなかったときは false
     */
    suspend fun save(columns: List<TimelineColumnSetting>): Boolean
}

sealed interface TimelineColumnSetting {
    data object Home : TimelineColumnSetting

    data class FollowingGroup(val groupId: String) : TimelineColumnSetting
}

val DEFAULT_TIMELINE_COLUMNS: List<TimelineColumnSetting> = listOf(TimelineColumnSetting.Home)
