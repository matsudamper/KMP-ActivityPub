package net.matsudamper.kmp.activitypub.android.server

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
private fun ServerSettingContentPreview() {
    MaterialTheme {
        Surface {
            ServerSettingContent(
                uiState = ServerSettingUiState(
                    serverUrlText = "https://example.com",
                    dialog = null,
                    listener = PreviewServerSettingListener,
                ),
            )
        }
    }
}

/**
 * ダイアログは別ウィンドウに出てスナップショットに写らないので、中身だけを表示する
 */
@Preview
@Composable
private fun ServerSettingDialogBodyPreview() {
    MaterialTheme {
        Surface {
            ServerSettingDialogBody(
                dialog = ServerSettingUiState.Dialog(
                    serverUrl = "example.com/path",
                    error = "https:// から始まるサーバーの URL を入れる（パスは付けない）",
                    saveButtonEnabled = true,
                ),
                listener = PreviewServerSettingListener,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

private object PreviewServerSettingListener : ServerSettingUiState.Listener {
    override fun onClickChange() = Unit

    override fun onServerUrlChanged(text: String) = Unit

    override fun onClickSave() = Unit

    override fun onDismissDialog() = Unit
}
