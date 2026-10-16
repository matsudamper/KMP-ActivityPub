package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun SettingsContentPreview() {
    MaterialTheme {
        SettingsContent(
            uiState = SettingsScreenUiState(
                addPasskeyButtonVisible = true,
                addPasskeyButtonEnabled = true,
                passkeyMessage = SettingsScreenUiState.PasskeyMessage(text = "パスキーを追加した", isError = false),
                listener = AndroidPreviewSettingsListener,
            ),
        )
    }
}

@Preview
@Composable
private fun SettingsContentUnavailablePreview() {
    MaterialTheme {
        SettingsContent(
            uiState = SettingsScreenUiState(
                addPasskeyButtonVisible = false,
                addPasskeyButtonEnabled = false,
                passkeyMessage = SettingsScreenUiState.PasskeyMessage(text = "この端末ではパスキーを追加できない", isError = false),
                listener = AndroidPreviewSettingsListener,
            ),
        )
    }
}

private object AndroidPreviewSettingsListener : SettingsScreenUiState.Listener {
    override fun onClickAddPasskey() = Unit

    override fun onClickBack() = Unit
}
