package net.matsudamper.kmp.activitypub.frontend.screen.languagesettings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageSetting
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageTag

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageSettingsScreenViewModelTest {
    @Test
    fun invalidLanguageDisablesSaveAndShowsError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeLanguageSettingStore()
            val viewModel = LanguageSettingsScreenViewModel(languageSettingStore = store)
            advanceUntilIdle()
            assertEquals("ja", viewModel.uiStateFlow.value.languageInput)
            assertTrue(viewModel.uiStateFlow.value.saveButtonEnabled)

            viewModel.uiStateFlow.value.listener.onChangeLanguageInput("日本語")
            advanceUntilIdle()
            assertTrue(viewModel.uiStateFlow.value.languageInputError)
            assertFalse(viewModel.uiStateFlow.value.saveButtonEnabled)

            viewModel.uiStateFlow.value.listener.onClickSave()
            advanceUntilIdle()
            assertEquals(LanguageTag.parse("ja"), store.languageSetting.value.languageTag)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun validLanguageIsSavedWithNormalizedCase() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeLanguageSettingStore()
            val viewModel = LanguageSettingsScreenViewModel(languageSettingStore = store)
            advanceUntilIdle()

            viewModel.uiStateFlow.value.listener.onChangeLanguageInput(" ZH-hant-tw ")
            viewModel.uiStateFlow.value.listener.onChangeTranslateButtonSwitch(true)
            advanceUntilIdle()
            assertFalse(viewModel.uiStateFlow.value.languageInputError)
            assertTrue(viewModel.uiStateFlow.value.saveButtonEnabled)

            viewModel.uiStateFlow.value.listener.onClickSave()
            advanceUntilIdle()
            assertEquals("zh-Hant-TW", store.languageSetting.value.languageTag?.value)
            assertTrue(store.languageSetting.value.translateButtonVisible)
            assertEquals("zh-Hant-TW", viewModel.uiStateFlow.value.languageInput)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class FakeLanguageSettingStore : LanguageSettingStore {
    val languageSetting = MutableStateFlow(
        LanguageSetting(languageTag = LanguageTag.parse("ja"), translateButtonVisible = false),
    )

    override val languageSettingFlow: Flow<LanguageSetting> = languageSetting

    override suspend fun save(languageTag: LanguageTag, translateButtonVisible: Boolean): Boolean {
        languageSetting.value = LanguageSetting(languageTag = languageTag, translateButtonVisible = translateButtonVisible)
        return true
    }
}
