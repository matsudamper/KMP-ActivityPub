package net.matsudamper.kmp.activitypub.frontend.screen.profileedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.screen.ProfileEditDialogFrame
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile

@Preview
@Composable
private fun ProfileEditContentPreview() {
    MaterialTheme {
        ProfileEditContent(
            uiState = ProfileEditScreenUiState(
                content = ProfileEditScreenUiState.Content.Loaded(
                    iconPreview = null,
                    iconLoadingVisible = false,
                    currentIconUrl = null,
                    iconSelectButtonEnabled = true,
                    headerPreview = null,
                    headerLoadingVisible = false,
                    currentHeaderUrl = null,
                    headerSelectButtonEnabled = true,
                    headerCropDialog = null,
                    displayName = "ユーザー1",
                    displayNameInputEnabled = true,
                    displayNameHasError = false,
                    summary = "Kotlin で ActivityPub サーバーを作っています。\nよろしくお願いします。",
                    summaryInputEnabled = true,
                    summaryHasError = false,
                    links = listOf(
                        ProfileEditScreenUiState.Link(
                            title = "リンク 1",
                            name = "GitHub",
                            url = "https://github.com/user1",
                            inputEnabled = true,
                            hasError = false,
                            removeButtonEnabled = true,
                        ),
                        ProfileEditScreenUiState.Link(
                            title = "リンク 2",
                            name = "Blog",
                            url = "ftp://blog.example",
                            inputEnabled = true,
                            hasError = true,
                            removeButtonEnabled = true,
                        ),
                    ),
                    addLinkButtonEnabled = true,
                    likesPublic = true,
                    likesPublicSwitchEnabled = true,
                    saveButtonEnabled = true,
                ),
                backButtonEnabled = true,
                listener = AndroidPreviewProfileEditListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun ProfileEditContentDialogPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            ProfileEditDialogFrame {
                ProfileEditContent(
                    uiState = ProfileEditScreenUiState(
                        content = ProfileEditScreenUiState.Content.Loaded(
                            iconPreview = null,
                            iconLoadingVisible = false,
                            currentIconUrl = null,
                            iconSelectButtonEnabled = true,
                            headerPreview = null,
                            headerLoadingVisible = false,
                            currentHeaderUrl = null,
                            headerSelectButtonEnabled = true,
                            headerCropDialog = null,
                            displayName = "ユーザー1",
                            displayNameInputEnabled = true,
                            displayNameHasError = false,
                            summary = "",
                            summaryInputEnabled = true,
                            summaryHasError = false,
                            links = listOf(),
                            addLinkButtonEnabled = true,
                            likesPublic = false,
                            likesPublicSwitchEnabled = true,
                            saveButtonEnabled = true,
                        ),
                        backButtonEnabled = true,
                        listener = AndroidPreviewProfileEditListener,
                    ),
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun ProfileEditContentErrorPreview() {
    MaterialTheme {
        ProfileEditContent(
            uiState = ProfileEditScreenUiState(
                content = ProfileEditScreenUiState.Content.Error(message = "ネットワークエラー"),
                backButtonEnabled = true,
                listener = AndroidPreviewProfileEditListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun ProfileEditContentIconUploadingPreview() {
    MaterialTheme {
        ProfileEditContent(
            uiState = ProfileEditScreenUiState(
                content = ProfileEditScreenUiState.Content.Loaded(
                    iconLoadingVisible = true,
                    iconPreview = ImageBitmap(width = 72, height = 72).apply {
                        Canvas(this).drawRect(0f, 0f, 72f, 72f, Paint().apply { color = Color(0xFF4A90D9) })
                    },
                    currentIconUrl = null,
                    iconSelectButtonEnabled = false,
                    headerPreview = null,
                    headerLoadingVisible = false,
                    currentHeaderUrl = null,
                    headerSelectButtonEnabled = false,
                    headerCropDialog = null,
                    displayName = "ユーザー1",
                    displayNameInputEnabled = true,
                    displayNameHasError = false,
                    summary = "",
                    summaryInputEnabled = true,
                    summaryHasError = false,
                    links = listOf(),
                    addLinkButtonEnabled = true,
                    likesPublic = false,
                    likesPublicSwitchEnabled = true,
                    saveButtonEnabled = false,
                ),
                backButtonEnabled = false,
                listener = AndroidPreviewProfileEditListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun ProfileEditContentHeaderUploadingPreview() {
    MaterialTheme {
        ProfileEditContent(
            uiState = ProfileEditScreenUiState(
                content = ProfileEditScreenUiState.Content.Loaded(
                    iconLoadingVisible = false,
                    iconPreview = null,
                    currentIconUrl = null,
                    iconSelectButtonEnabled = false,
                    headerPreview = ImageBitmap(width = 300, height = 100).apply {
                        Canvas(this).drawRect(0f, 0f, 300f, 100f, Paint().apply { color = Color(0xFFD98C4A) })
                    },
                    headerLoadingVisible = true,
                    currentHeaderUrl = null,
                    headerSelectButtonEnabled = false,
                    headerCropDialog = null,
                    displayName = "ユーザー1",
                    displayNameInputEnabled = true,
                    displayNameHasError = false,
                    summary = "",
                    summaryInputEnabled = true,
                    summaryHasError = false,
                    links = listOf(),
                    addLinkButtonEnabled = true,
                    likesPublic = false,
                    likesPublicSwitchEnabled = true,
                    saveButtonEnabled = false,
                ),
                backButtonEnabled = false,
                listener = AndroidPreviewProfileEditListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun HeaderCropDialogContentPreview() {
    MaterialTheme {
        HeaderCropDialogContent(
            dialog = ProfileEditScreenUiState.HeaderCropDialog(
                image = ImageBitmap(width = 300, height = 400).apply {
                    val canvas = Canvas(this)
                    canvas.drawRect(0f, 0f, 300f, 200f, Paint().apply { color = Color(0xFF4A90D9) })
                    canvas.drawRect(0f, 200f, 300f, 400f, Paint().apply { color = Color(0xFFD98C4A) })
                },
                cropTopRatio = 0.5f,
                cropHeightRatio = 0.25f,
            ),
            listener = AndroidPreviewProfileEditListener,
        )
    }
}

private object AndroidPreviewProfileEditListener : ProfileEditScreenUiState.Listener {
    override fun onClickSelectIcon() = Unit

    override fun onIconPicked(file: PickedImageFile) = Unit

    override fun onClickSelectHeader() = Unit

    override fun onHeaderPicked(file: PickedImageFile) = Unit

    override fun onHeaderCropMoved(cropTopRatio: Float) = Unit

    override fun onClickHeaderCropConfirm() = Unit

    override fun onClickHeaderCropCancel() = Unit

    override fun onDisplayNameChanged(text: String) = Unit

    override fun onSummaryChanged(text: String) = Unit

    override fun onLinkNameChanged(
        index: Int,
        text: String,
    ) = Unit

    override fun onLinkUrlChanged(
        index: Int,
        text: String,
    ) = Unit

    override fun onClickAddLink() = Unit

    override fun onClickRemoveLink(index: Int) = Unit

    override fun onLikesPublicChanged(likesPublic: Boolean) = Unit

    override fun onClickSave() = Unit

    override fun onClickRetry() = Unit

    override fun onClickBack() = Unit
}
