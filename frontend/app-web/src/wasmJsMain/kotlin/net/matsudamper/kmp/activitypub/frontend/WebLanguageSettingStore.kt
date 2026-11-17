package net.matsudamper.kmp.activitypub.frontend

import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageSetting
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageTag

internal class WebLanguageSettingStore : LanguageSettingStore {
    private val languageSettingStateFlow = MutableStateFlow(loadLanguageSetting())

    override val languageSettingFlow: Flow<LanguageSetting> = languageSettingStateFlow.asStateFlow()

    /**
     * ストレージを拒否されたブラウザでも、開いている間だけは設定を変えられるようにする
     */
    override suspend fun save(languageTag: LanguageTag, translateButtonVisible: Boolean): Boolean {
        val saved = runCatching {
            localStorage.setItem(LANGUAGE_TAG_KEY, languageTag.value)
            localStorage.setItem(TRANSLATE_BUTTON_VISIBLE_KEY, translateButtonVisible.toString())
        }.isSuccess
        languageSettingStateFlow.value = LanguageSetting(
            languageTag = languageTag,
            translateButtonVisible = translateButtonVisible,
        )
        return saved
    }

    /**
     * ストレージを拒否されると getItem が例外を投げ、生成時に読むのでアプリごと起動しなくなる
     */
    private fun loadLanguageSetting(): LanguageSetting {
        val savedLanguageTag = runCatching { localStorage.getItem(LANGUAGE_TAG_KEY) }.getOrNull()
        val savedTranslateButtonVisible = runCatching { localStorage.getItem(TRANSLATE_BUTTON_VISIBLE_KEY) }.getOrNull()
        return LanguageSetting(
            languageTag = LanguageTag.parse(savedLanguageTag ?: window.navigator.language),
            translateButtonVisible = savedTranslateButtonVisible == true.toString(),
        )
    }
}

private const val LANGUAGE_TAG_KEY = "languageTag"
private const val TRANSLATE_BUTTON_VISIBLE_KEY = "translateButtonVisible"
