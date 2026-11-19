package net.matsudamper.kmp.activitypub.frontend.screen.admin.unknownobjecttypes

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun UnknownTypesPreview() {
    MaterialTheme {
        UnknownObjectTypesContent(
            uiState = UnknownObjectTypesScreenUiState(
                content = UnknownObjectTypesScreenUiState.Content.Loaded(
                    types = listOf(
                        previewType(
                            typeName = "Article",
                            sourceUrl = "https://blog.example/articles/2026/11/18/hello",
                            seenText = "2026/11/18 21:00 に受け取った",
                        ),
                        previewType(
                            typeName = "PropertyValue",
                            sourceUrl = "https://very-long-hostname-of-remote-server.example/users/someone-with-a-long-name/statuses/1234567890",
                            seenText = "3 回受け取った。最後は 2026/11/18 20:30、最初は 2026/11/18 09:00",
                        ),
                        previewType(
                            typeName = "https://example.org/ns#CustomMedia",
                            sourceUrl = "https://remote.example/notes/1",
                            seenText = "2026/11/17 12:00 に受け取った",
                        ),
                    ),
                    footer = UnknownObjectTypesScreenUiState.Footer.None,
                ),
                listener = AndroidPreviewUnknownObjectTypesListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun UnknownTypesEmptyPreview() {
    MaterialTheme {
        UnknownObjectTypesContent(
            uiState = UnknownObjectTypesScreenUiState(
                content = UnknownObjectTypesScreenUiState.Content.Empty,
                listener = AndroidPreviewUnknownObjectTypesListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private fun previewType(
    typeName: String,
    sourceUrl: String,
    seenText: String,
): UnknownObjectTypesScreenUiState.UnknownObjectType = UnknownObjectTypesScreenUiState.UnknownObjectType(
    listKey = "$typeName $sourceUrl",
    typeName = typeName,
    sourceUrl = sourceUrl,
    seenText = seenText,
    listener = AndroidPreviewUnknownObjectTypeListener,
)

private object AndroidPreviewUnknownObjectTypeListener : UnknownObjectTypesScreenUiState.UnknownObjectTypeListener {
    override fun onClickSourceUrl() = Unit
}

private object AndroidPreviewUnknownObjectTypesListener : UnknownObjectTypesScreenUiState.Listener {
    override fun onClickBack() = Unit

    override fun onClickRetry() = Unit

    override fun onScrolledToEnd() = Unit

    override fun onClickRetryLoadMore() = Unit
}
