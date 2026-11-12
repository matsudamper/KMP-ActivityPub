package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun AdminContentRemoteProfileRefreshPreview() {
    MaterialTheme {
        AdminContent(
            uiState = AdminScreenUiState(
                content = AdminScreenUiState.Content.Loaded(
                    remoteProfileRefresh = AdminScreenUiState.RemoteProfileRefresh(
                        statusText = "取り直し中（2026-11-11T06:00:00Z 開始）\n120 人中 48 人を取り直し、失敗 2 人、スキップ 3 人",
                        startButtonEnabled = false,
                        listener = AndroidPreviewRemoteProfileRefreshListener,
                    ),
                ),
                listener = AndroidPreviewAdminListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
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
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private object AndroidPreviewAdminListener : AdminScreenUiState.Listener {
    override fun onClickBack() = Unit
}

private object AndroidPreviewRemoteProfileRefreshListener : AdminScreenUiState.RemoteProfileRefreshListener {
    override fun onClickStart() = Unit
}
