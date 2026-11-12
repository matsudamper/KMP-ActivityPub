package net.matsudamper.kmp.activitypub.frontend.logic.link

import kotlinx.coroutines.flow.Flow

/**
 * 端末ごとに覚えておくリンクの開き方。サーバーには送らない
 */
interface LinkOpenSettingStore {
    /**
     * 空のときは開き方を選べないプラットフォーム
     */
    val selectableModes: List<LinkOpenMode>

    val linkOpenModeFlow: Flow<LinkOpenMode>

    /**
     * @return 端末に書き込めなかったときは false
     */
    suspend fun save(linkOpenMode: LinkOpenMode): Boolean
}
