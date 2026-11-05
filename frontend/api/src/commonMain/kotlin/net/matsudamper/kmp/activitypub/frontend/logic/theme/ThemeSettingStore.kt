package net.matsudamper.kmp.activitypub.frontend.logic.theme

import kotlinx.coroutines.flow.Flow

/**
 * 端末ごとに覚えておく表示テーマ。サーバーには送らない
 */
interface ThemeSettingStore {
    val themeModeFlow: Flow<ThemeMode>

    /**
     * @return 端末に書き込めなかったときは false
     */
    suspend fun save(themeMode: ThemeMode): Boolean
}
