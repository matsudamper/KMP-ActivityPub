package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.screen.theme.AppTheme

@Preview
@Composable
private fun SettingsContentPreview() {
    MaterialTheme {
        SettingsContent(uiState = previewSettingsUiState, snackbarHostState = SnackbarHostState())
    }
}

@Preview
@Composable
private fun SettingsContentDarkPreview() {
    AppTheme(darkTheme = true) {
        SettingsContent(
            uiState = previewSettingsUiState.copy(
                themeOptions = previewThemeOptions(selected = 2),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private fun previewThemeOptions(selected: Int): List<SettingsScreenUiState.ThemeOption> =
    listOf("端末の設定に合わせる", "ライト", "ダーク").mapIndexed { index, label ->
        SettingsScreenUiState.ThemeOption(
            label = label,
            selected = index == selected,
            listener = AndroidPreviewThemeOptionListener,
        )
    }

private val previewSettingsUiState = SettingsScreenUiState(
    themeOptions = previewThemeOptions(selected = 0),
    logoutButtonEnabled = true,
    listener = AndroidPreviewSettingsListener,
)

private object AndroidPreviewThemeOptionListener : SettingsScreenUiState.ThemeOptionListener {
    override fun onClick() = Unit
}

private object AndroidPreviewSettingsListener : SettingsScreenUiState.Listener {
    override fun onClickSecurity() = Unit

    override fun onClickGitHubRelease() = Unit

    override fun onClickBack() = Unit

    override fun onClickLogout() = Unit
}
