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
                listener = AndroidPreviewSettingsListener,
            ),
        )
    }
}

private object AndroidPreviewSettingsListener : SettingsScreenUiState.Listener {
    override fun onClickBack() = Unit

    override fun onClickLogout() = Unit
}
