package net.matsudamper.kmp.activitypub.frontend.screen.customemojis

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile

@Preview
@Composable
private fun CustomEmojisContentPreview() {
    MaterialTheme {
        CustomEmojisContent(
            uiState = CustomEmojisScreenUiState(
                shortcodeInput = "kotlin",
                shortcodeInputEnabled = true,
                pickedImage = null,
                pickImageButtonEnabled = true,
                addButtonEnabled = false,
                content = CustomEmojisScreenUiState.Content.Loaded(
                    emojis = listOf(
                        CustomEmojisScreenUiState.Emoji(
                            shortcodeText = ":kotlin:",
                            imageUrl = "https://example.com/kotlin.png",
                            deleteButton = CustomEmojisScreenUiState.DeleteButton(enabled = true, listener = AndroidPreviewDeleteButtonListener),
                        ),
                        CustomEmojisScreenUiState.Emoji(
                            shortcodeText = ":party:",
                            imageUrl = "https://example.com/party.png",
                            deleteButton = null,
                        ),
                    ),
                    emptyMessageVisible = false,
                ),
                listener = AndroidPreviewListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun CustomEmojisContentEmptyPreview() {
    MaterialTheme {
        CustomEmojisContent(
            uiState = CustomEmojisScreenUiState(
                shortcodeInput = "",
                shortcodeInputEnabled = true,
                pickedImage = null,
                pickImageButtonEnabled = true,
                addButtonEnabled = false,
                content = CustomEmojisScreenUiState.Content.Loaded(emojis = listOf(), emptyMessageVisible = true),
                listener = AndroidPreviewListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private object AndroidPreviewDeleteButtonListener : CustomEmojisScreenUiState.DeleteButtonListener {
    override fun onClick() = Unit
}

private object AndroidPreviewListener : CustomEmojisScreenUiState.Listener {
    override fun onClickBack() = Unit

    override fun onChangeShortcodeInput(value: String) = Unit

    override fun onClickPickImage() = Unit

    override fun onImagePicked(file: PickedImageFile) = Unit

    override fun onClickAdd() = Unit

    override fun onClickRetry() = Unit
}
