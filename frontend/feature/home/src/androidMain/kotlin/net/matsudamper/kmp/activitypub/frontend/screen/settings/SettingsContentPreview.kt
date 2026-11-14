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
                linkOpenOptions = previewLinkOpenOptions(selected = 1),
                adminItemVisible = true,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private fun previewThemeOptions(selected: Int): List<SettingsScreenUiState.RadioOption> =
    previewRadioOptions(labels = listOf("端末の設定に合わせる", "ライト", "ダーク"), selected = selected)

private fun previewLinkOpenOptions(selected: Int): List<SettingsScreenUiState.RadioOption> =
    previewRadioOptions(labels = listOf("アプリ内（カスタムタブ）", "外部ブラウザ"), selected = selected)

private fun previewRadioOptions(labels: List<String>, selected: Int): List<SettingsScreenUiState.RadioOption> =
    labels.mapIndexed { index, label ->
        SettingsScreenUiState.RadioOption(
            label = label,
            selected = index == selected,
            listener = AndroidPreviewRadioOptionListener,
        )
    }

private val previewSettingsUiState = SettingsScreenUiState(
    themeOptions = previewThemeOptions(selected = 0),
    linkOpenSectionVisible = true,
    linkOpenOptions = previewLinkOpenOptions(selected = 0),
    logoutButtonEnabled = true,
    adminItemVisible = false,
    listener = AndroidPreviewSettingsListener,
)

private object AndroidPreviewRadioOptionListener : SettingsScreenUiState.RadioOptionListener {
    override fun onClick() = Unit
}

private object AndroidPreviewSettingsListener : SettingsScreenUiState.Listener {
    override fun onClickSecurity() = Unit

    override fun onClickFollowingGroups() = Unit

    override fun onClickBlockedServers() = Unit

    override fun onClickAdmin() = Unit

    override fun onClickGitHubRelease() = Unit

    override fun onClickBack() = Unit

    override fun onClickLogout() = Unit
}
