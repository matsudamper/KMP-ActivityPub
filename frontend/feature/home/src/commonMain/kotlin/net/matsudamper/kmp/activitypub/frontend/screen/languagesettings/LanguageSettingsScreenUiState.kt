package net.matsudamper.kmp.activitypub.frontend.screen.languagesettings

import androidx.compose.runtime.Immutable

data class LanguageSettingsScreenUiState(
    val languageInput: String,
    val languageInputEnabled: Boolean,
    val languageInputError: Boolean,
    val languageInputSupportingText: String,
    val translateButtonSwitchChecked: Boolean,
    val translateButtonSwitchEnabled: Boolean,
    val saveButtonEnabled: Boolean,
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onChangeLanguageInput(text: String)

        fun onChangeTranslateButtonSwitch(checked: Boolean)

        fun onClickSave()

        fun onClickBack()
    }
}
