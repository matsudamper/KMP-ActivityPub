package net.matsudamper.kmp.activitypub.frontend.screen.admin.deletedremotenotes

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun DeletedRemoteNotesPreview() {
    MaterialTheme {
        DeletedRemoteNotesContent(
            uiState = previewUiState(purgeAllDialog = null),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun DeletedRemoteNotesPurgeAllDialogPreview() {
    MaterialTheme {
        DeletedRemoteNotesContent(
            uiState = previewUiState(
                purgeAllDialog = DeletedRemoteNotesScreenUiState.PurgeAllDialog(
                    message = "投稿者が消した他のサーバーの投稿を全て、付いていたいいね・ブースト・通知ごと削除します。" +
                        "タイムラインの削除済みの表示も消えます。元に戻せません。",
                    buttonsEnabled = true,
                    listener = AndroidPreviewPurgeAllDialogListener,
                ),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private fun previewUiState(
    purgeAllDialog: DeletedRemoteNotesScreenUiState.PurgeAllDialog?,
): DeletedRemoteNotesScreenUiState = DeletedRemoteNotesScreenUiState(
    content = DeletedRemoteNotesScreenUiState.Content.Loaded(
        notes = listOf(
            previewNote(
                listKey = "1",
                actorText = "alice（https://remote.example/users/alice）",
                contentText = "間違えて投稿したので消します",
                purgeButtonEnabled = true,
            ),
            previewNote(
                listKey = "2",
                actorText = "https://very-long-hostname-of-remote-server.example/users/someone-with-a-long-name",
                contentText = "とても長い本文。".repeat(20),
                purgeButtonEnabled = false,
            ),
        ),
        footer = DeletedRemoteNotesScreenUiState.Footer.None,
    ),
    purgeAllButtonEnabled = true,
    purgeAllDialog = purgeAllDialog,
    listener = AndroidPreviewListener,
)

private fun previewNote(
    listKey: String,
    actorText: String,
    contentText: String,
    purgeButtonEnabled: Boolean,
): DeletedRemoteNotesScreenUiState.DeletedNote = DeletedRemoteNotesScreenUiState.DeletedNote(
    listKey = listKey,
    actorText = actorText,
    contentText = contentText,
    objectUri = "https://remote.example/notes/$listKey",
    dateText = "2026/11/18 21:00 に投稿、2026/11/19 09:00 に削除",
    purgeButtonEnabled = purgeButtonEnabled,
    listener = AndroidPreviewNoteListener,
)

private object AndroidPreviewListener : DeletedRemoteNotesScreenUiState.Listener {
    override fun onClickBack() = Unit

    override fun onClickRetry() = Unit

    override fun onScrolledToEnd() = Unit

    override fun onClickRetryLoadMore() = Unit

    override fun onClickPurgeAll() = Unit
}

private object AndroidPreviewNoteListener : DeletedRemoteNotesScreenUiState.DeletedNoteListener {
    override fun onClickObjectUri() = Unit

    override fun onClickPurge() = Unit
}

private object AndroidPreviewPurgeAllDialogListener : DeletedRemoteNotesScreenUiState.PurgeAllDialogListener {
    override fun onClickPurge() = Unit

    override fun onDismiss() = Unit
}
