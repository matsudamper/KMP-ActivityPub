package net.matsudamper.kmp.activitypub.frontend.screen.languagesettings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.screen.theme.AppTheme

@Preview
@Composable
private fun LanguageSettingsContentPreview() {
    MaterialTheme {
        LanguageSettingsContent(uiState = previewLanguageSettingsUiState, snackbarHostState = SnackbarHostState())
    }
}

@Preview
@Composable
private fun LanguageSettingsContentInvalidDarkPreview() {
    AppTheme(darkTheme = true) {
        LanguageSettingsContent(
            uiState = previewLanguageSettingsUiState.copy(
                languageInput = "日本語",
                languageInputError = true,
                languageInputSupportingText = "有効な言語ではありません。例: ja, en-US",
                translateButtonSwitchChecked = false,
                saveButtonEnabled = false,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private val previewLanguageSettingsUiState = LanguageSettingsScreenUiState(
    languageInput = "ja",
    languageInputEnabled = true,
    languageInputError = false,
    languageInputSupportingText = "例: ja, en-US",
    translateButtonSwitchChecked = true,
    translateButtonSwitchEnabled = true,
    saveButtonEnabled = true,
    listener = AndroidPreviewLanguageSettingsListener,
)

private object AndroidPreviewLanguageSettingsListener : LanguageSettingsScreenUiState.Listener {
    override fun onChangeLanguageInput(text: String) = Unit

    override fun onChangeTranslateButtonSwitch(checked: Boolean) = Unit

    override fun onClickSave() = Unit

    override fun onClickBack() = Unit
}
