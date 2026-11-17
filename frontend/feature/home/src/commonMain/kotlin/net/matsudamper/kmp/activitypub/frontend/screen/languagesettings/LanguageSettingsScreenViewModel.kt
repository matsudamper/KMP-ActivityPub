package net.matsudamper.kmp.activitypub.frontend.screen.languagesettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageTag
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class LanguageSettingsScreenViewModel(
    private val languageSettingStore: LanguageSettingStore,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                loaded = false,
                languageInput = "",
                translateButtonVisible = false,
                saving = false,
            ),
        )

    private val listener =
        object : LanguageSettingsScreenUiState.Listener {
            override fun onChangeLanguageInput(text: String) {
                viewModelStateFlow.update { it.copy(languageInput = text) }
            }

            override fun onChangeTranslateButtonSwitch(checked: Boolean) {
                viewModelStateFlow.update { it.copy(translateButtonVisible = checked) }
            }

            override fun onClickSave() {
                save()
            }

            override fun onClickBack() {
                viewModelScope.launch {
                    events.send { it.navigateBack() }
                }
            }
        }

    val uiStateFlow: StateFlow<LanguageSettingsScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.value = createUiState(viewModelState)
                }
            }
        }.asStateFlow()

    init {
        viewModelScope.launch {
            val languageSetting = languageSettingStore.languageSettingFlow.first()
            viewModelStateFlow.update {
                it.copy(
                    loaded = true,
                    languageInput = languageSetting.languageTag?.value.orEmpty(),
                    translateButtonVisible = languageSetting.translateButtonVisible,
                )
            }
        }
    }

    private fun save() {
        val viewModelState = viewModelStateFlow.value
        val languageTag = LanguageTag.parse(viewModelState.languageInput)
        if (viewModelState.loaded.not() || viewModelState.saving || languageTag == null) return

        viewModelStateFlow.update { it.copy(saving = true) }
        viewModelScope.launch {
            val saved =
                try {
                    languageSettingStore.save(
                        languageTag = languageTag,
                        translateButtonVisible = viewModelState.translateButtonVisible,
                    )
                } finally {
                    viewModelStateFlow.update { it.copy(saving = false) }
                }
            if (saved) {
                viewModelStateFlow.update { it.copy(languageInput = languageTag.value) }
                events.send { it.showMessage("言語の設定を保存した") }
            } else {
                events.send { it.showMessage("言語の設定を保存できなかった") }
            }
        }
    }

    private fun createUiState(viewModelState: ViewModelState): LanguageSettingsScreenUiState {
        val inputEnabled = viewModelState.loaded && viewModelState.saving.not()
        val languageValid = LanguageTag.parse(viewModelState.languageInput) != null
        val languageInputError = viewModelState.loaded && languageValid.not()
        return LanguageSettingsScreenUiState(
            languageInput = viewModelState.languageInput,
            languageInputEnabled = inputEnabled,
            languageInputError = languageInputError,
            languageInputSupportingText =
            if (languageInputError) {
                "有効な言語ではありません。例: ja, en-US"
            } else {
                "例: ja, en-US"
            },
            translateButtonSwitchChecked = viewModelState.translateButtonVisible,
            translateButtonSwitchEnabled = inputEnabled,
            saveButtonEnabled = inputEnabled && languageValid,
            listener = listener,
        )
    }

    internal interface Event {
        fun navigateBack()

        fun showMessage(message: String)
    }

    private data class ViewModelState(
        val loaded: Boolean,
        val languageInput: String,
        val translateButtonVisible: Boolean,
        val saving: Boolean,
    )
}
