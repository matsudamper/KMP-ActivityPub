package net.matsudamper.kmp.activitypub.frontend.logic.timeline

import kotlinx.coroutines.flow.Flow

/**
 * 横に広い画面で、仕切りを動かして変えたカラムの幅。端末ごとに覚えておき、サーバーには送らない
 */
interface ColumnWidthStore {
    /**
     * カラムの鍵ごとの幅（dp）。幅を変えていないカラムは含まない
     */
    val widthsFlow: Flow<Map<String, Float>>

    /**
     * @return 端末に書き込めなかったときは false
     */
    suspend fun save(widths: Map<String, Float>): Boolean
}
