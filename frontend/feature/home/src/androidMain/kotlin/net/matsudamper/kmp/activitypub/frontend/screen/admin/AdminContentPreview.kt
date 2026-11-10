package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun AdminContentEmptyPreview() {
    MaterialTheme {
        AdminContent(uiState = AdminScreenUiState(content = AdminScreenUiState.Content.Empty, listener = AndroidPreviewAdminListener))
    }
}

@Preview
@Composable
private fun AdminContentDeniedPreview() {
    MaterialTheme {
        AdminContent(
            uiState = AdminScreenUiState(
                content = AdminScreenUiState.Content.Message("サーバー管理画面に入る権限がない"),
                listener = AndroidPreviewAdminListener,
            ),
        )
    }
}

private object AndroidPreviewAdminListener : AdminScreenUiState.Listener {
    override fun onClickBack() = Unit
}
