package net.matsudamper.kmp.activitypub.frontend.logic.timeline

import kotlinx.coroutines.flow.Flow

/**
 * 端末ごとに覚えておく、タイムラインのカラムやタブ。サーバーには送らない
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

/**
 * @param id 名前や流す購読先を変えても同じカラムだと分かるための鍵
 * @param sources このカラムに流す購読先
 */
data class TimelineColumnSetting(
    val id: String,
    val name: String,
    val sources: Set<TimelineSource>,
)

sealed interface TimelineSource {
    data object MainGroup : TimelineSource

    data class FollowingGroup(val groupId: String) : TimelineSource

    /**
     * 自分の投稿に届いたお気に入りとブースト
     */
    data object Notifications : TimelineSource
}

val DEFAULT_TIMELINE_COLUMNS: List<TimelineColumnSetting> = listOf(
    TimelineColumnSetting(id = "main", name = "メイン", sources = setOf(TimelineSource.MainGroup)),
)
