package net.matsudamper.kmp.activitypub.frontend.screen.admin.deliverytargetlimit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun DeliveryTargetLimitContentPreview() {
    MaterialTheme {
        DeliveryTargetLimitContent(
            uiState = DeliveryTargetLimitScreenUiState(
                content = DeliveryTargetLimitScreenUiState.Content.Loaded(
                    limitText = "20",
                    hosts = listOf(
                        previewHost(host = "no-shared-inbox.example", countText = "24 / 20", rowHighlighted = true),
                        previewHost(host = "small-server.example", countText = "20 / 20", rowHighlighted = true),
                        previewHost(
                            host = "very-long-hostname-of-a-remote-server-without-shared-inbox.example",
                            countText = "7 / 20",
                            rowHighlighted = false,
                        ),
                        previewHost(host = "mastodon.example", countText = "1 / 20", rowHighlighted = false),
                    ),
                    emptyMessageVisible = false,
                ),
                listener = AndroidPreviewDeliveryTargetLimitListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun DeliveryTargetLimitEmptyPreview() {
    MaterialTheme {
        DeliveryTargetLimitContent(
            uiState = DeliveryTargetLimitScreenUiState(
                content = DeliveryTargetLimitScreenUiState.Content.Loaded(
                    limitText = "20",
                    hosts = listOf(),
                    emptyMessageVisible = true,
                ),
                listener = AndroidPreviewDeliveryTargetLimitListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private fun previewHost(
    host: String,
    countText: String,
    rowHighlighted: Boolean,
): DeliveryTargetLimitScreenUiState.Host = DeliveryTargetLimitScreenUiState.Host(
    host = host,
    countText = countText,
    rowHighlighted = rowHighlighted,
    warningText = if (rowHighlighted) "上限に達しています。配る先が増えるフォローは断ります" else null,
)

private object AndroidPreviewDeliveryTargetLimitListener : DeliveryTargetLimitScreenUiState.Listener {
    override fun onClickBack() = Unit

    override fun onClickRetry() = Unit
}
