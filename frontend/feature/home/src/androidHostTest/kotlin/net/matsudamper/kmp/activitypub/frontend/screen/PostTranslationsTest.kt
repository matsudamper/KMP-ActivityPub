package net.matsudamper.kmp.activitypub.frontend.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageSetting
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageTag
import net.matsudamper.kmp.activitypub.frontend.logic.language.TextTranslator

@OptIn(ExperimentalCoroutinesApi::class)
class PostTranslationsTest {
    @Test
    fun toggleTranslatesThenRestoresTheOriginal() = runTest {
        var failedCount = 0
        val translations = PostTranslations(
            translator = FakeTextTranslator(translatedBySourceText = mapOf("hello" to "こんにちは")),
            languageSettingStore = FakeLanguageSettingStore(),
            coroutineScope = backgroundScope,
            onFailed = { failedCount++ },
        )
        runCurrent()

        translations.toggle(PostTranslationSource(text = "hello", languageTag = "en"))
        assertEquals(PostTranslationProgress.Translating, translations.stateFlow.value.progressBySource[PostTranslationSource(text = "hello", languageTag = "en")])
        runCurrent()
        assertEquals(PostTranslationProgress.Translated("こんにちは"), translations.stateFlow.value.progressBySource[PostTranslationSource(text = "hello", languageTag = "en")])

        translations.toggle(PostTranslationSource(text = "hello", languageTag = "en"))
        assertEquals(mapOf(), translations.stateFlow.value.progressBySource)
        assertEquals(0, failedCount)
    }

    @Test
    fun changingTheViewerLanguageDiscardsTranslations() = runTest {
        val languageSettingStore = FakeLanguageSettingStore()
        val translations = PostTranslations(
            translator = FakeTextTranslator(translatedBySourceText = mapOf("hello" to "こんにちは", "bye" to "さようなら")),
            languageSettingStore = languageSettingStore,
            coroutineScope = backgroundScope,
            onFailed = {},
        )
        runCurrent()
        translations.toggle(PostTranslationSource(text = "hello", languageTag = "en"))
        runCurrent()
        translations.toggle(PostTranslationSource(text = "bye", languageTag = "en"))

        languageSettingStore.languageSetting.value = LanguageSetting(languageTag = LanguageTag.parse("en"), translateButtonVisible = true)
        runCurrent()
        assertEquals(mapOf(), translations.stateFlow.value.progressBySource)
    }

    @Test
    fun failedTranslationKeepsTheOriginalAndReports() = runTest {
        var failedCount = 0
        val translations = PostTranslations(
            translator = FakeTextTranslator(translatedBySourceText = mapOf()),
            languageSettingStore = FakeLanguageSettingStore(),
            coroutineScope = backgroundScope,
            onFailed = { failedCount++ },
        )
        runCurrent()

        translations.toggle(PostTranslationSource(text = "hello", languageTag = "en"))
        runCurrent()
        assertEquals(mapOf(), translations.stateFlow.value.progressBySource)
        assertEquals(1, failedCount)
    }
}

private class FakeTextTranslator(private val translatedBySourceText: Map<String, String>) : TextTranslator {
    override val supported: Boolean = true

    override suspend fun translate(text: String, sourceLanguageTag: String, targetLanguage: LanguageTag): String? = translatedBySourceText[text]
}

private class FakeLanguageSettingStore : LanguageSettingStore {
    val languageSetting = MutableStateFlow(LanguageSetting(languageTag = LanguageTag.parse("ja"), translateButtonVisible = true))

    override val languageSettingFlow: Flow<LanguageSetting> = languageSetting

    override suspend fun save(languageTag: LanguageTag, translateButtonVisible: Boolean): Boolean = true
}
