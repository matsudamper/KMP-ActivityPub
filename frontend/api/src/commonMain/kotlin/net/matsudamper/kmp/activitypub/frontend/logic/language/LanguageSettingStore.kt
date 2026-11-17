package net.matsudamper.kmp.activitypub.frontend.logic.language

import kotlinx.coroutines.flow.Flow

/**
 * 端末ごとに覚えておく言語の設定。サーバーには送らない
 */
interface LanguageSettingStore {
    val languageSettingFlow: Flow<LanguageSetting>

    /**
     * @return 端末に書き込めなかったときは false
     */
    suspend fun save(languageTag: LanguageTag, translateButtonVisible: Boolean): Boolean
}
