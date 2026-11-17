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

        translations.toggle(sourceText = "hello", sourceLanguageTag = "en")
        assertEquals(PostTranslationProgress.Translating, translations.stateFlow.value.progressBySourceText["hello"])
        runCurrent()
        assertEquals(PostTranslationProgress.Translated("こんにちは"), translations.stateFlow.value.progressBySourceText["hello"])

        translations.toggle(sourceText = "hello", sourceLanguageTag = "en")
        assertEquals(mapOf(), translations.stateFlow.value.progressBySourceText)
        assertEquals(0, failedCount)
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

        translations.toggle(sourceText = "hello", sourceLanguageTag = "en")
        runCurrent()
        assertEquals(mapOf(), translations.stateFlow.value.progressBySourceText)
        assertEquals(1, failedCount)
    }
}

private class FakeTextTranslator(private val translatedBySourceText: Map<String, String>) : TextTranslator {
    override val supported: Boolean = true

    override suspend fun translate(text: String, sourceLanguageTag: String, targetLanguage: LanguageTag): String? = translatedBySourceText[text]
}

private class FakeLanguageSettingStore : LanguageSettingStore {
    override val languageSettingFlow: Flow<LanguageSetting> =
        MutableStateFlow(LanguageSetting(languageTag = LanguageTag.parse("ja"), translateButtonVisible = true))

    override suspend fun save(languageTag: LanguageTag, translateButtonVisible: Boolean): Boolean = true
}
