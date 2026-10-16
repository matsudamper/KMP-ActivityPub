package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun SettingsContentPreview() {
    MaterialTheme {
        SettingsContent(uiState = previewSettingsUiState)
    }
}

@Preview
@Composable
private fun SettingsContentEmptyPreview() {
    MaterialTheme {
        SettingsContent(
            uiState = previewSettingsUiState.copy(
                passkeyItems = listOf(),
                passkeyListText = "登録したパスキーは無い",
                passkeyName = "",
                addPasskeyButtonEnabled = false,
                passkeyMessage = null,
            ),
        )
    }
}

@Preview
@Composable
private fun SettingsContentDeleteDialogPreview() {
    MaterialTheme {
        SettingsContent(
            uiState = previewSettingsUiState.copy(
                deleteDialog = SettingsScreenUiState.DeleteDialog(passkeyName = "Pixel", deleteButtonEnabled = true),
            ),
        )
    }
}

@Preview
@Composable
private fun SettingsContentUnavailablePreview() {
    MaterialTheme {
        SettingsContent(
            uiState = previewSettingsUiState.copy(
                passkeyNameInputVisible = false,
                addPasskeyButtonVisible = false,
                passkeyMessage = SettingsScreenUiState.PasskeyMessage(text = "この端末ではパスキーを追加できない", isError = false),
            ),
        )
    }
}

private val previewSettingsUiState = SettingsScreenUiState(
    passkeyItems = listOf(
        SettingsScreenUiState.PasskeyItem(name = "Pixel", deleteButtonEnabled = true, listener = AndroidPreviewPasskeyItemListener),
        SettingsScreenUiState.PasskeyItem(name = "MacBook", deleteButtonEnabled = true, listener = AndroidPreviewPasskeyItemListener),
    ),
    passkeyListText = null,
    passkeyNameInputVisible = true,
    passkeyName = "iPhone",
    passkeyNameInputEnabled = true,
    addPasskeyButtonVisible = true,
    addPasskeyButtonEnabled = true,
    passkeyMessage = SettingsScreenUiState.PasskeyMessage(text = "パスキーを追加した", isError = false),
    deleteDialog = null,
    listener = AndroidPreviewSettingsListener,
)

private object AndroidPreviewPasskeyItemListener : SettingsScreenUiState.PasskeyItemListener {
    override fun onClickDelete() = Unit
}

private object AndroidPreviewSettingsListener : SettingsScreenUiState.Listener {
    override fun onPasskeyNameChanged(text: String) = Unit

    override fun onClickAddPasskey() = Unit

    override fun onClickConfirmDelete() = Unit

    override fun onDismissDeleteDialog() = Unit

    override fun onClickBack() = Unit
}
