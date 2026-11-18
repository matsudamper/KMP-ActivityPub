package net.matsudamper.kmp.activitypub.frontend.screen.admin.faileddeliveries

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun FailedDeliveriesContentPreview() {
    MaterialTheme {
        FailedDeliveriesContent(
            uiState = FailedDeliveriesScreenUiState(
                content = FailedDeliveriesScreenUiState.Content.Loaded(
                    deliveries = listOf(
                        previewDelivery(
                            listKey = "3",
                            kindText = "投稿",
                            inbox = "https://gone.example/users/alice/inbox",
                            failedAtText = "2026/11/08 21:00 に諦めた",
                            errorText = "HTTP 410 Gone",
                        ),
                        previewDelivery(
                            listKey = "2",
                            kindText = "フォローの承認",
                            inbox = "https://very-long-hostname-of-remote-server.example/users/someone-with-a-long-name/inbox",
                            failedAtText = "2026/11/07 09:30 に諦めた",
                            errorText = "java.net.ConnectException: 相手のサーバーにつながらなかった。7 日送り直しても届かなかったので諦めた",
                        ),
                        previewDelivery(
                            listKey = "1",
                            kindText = "いいね",
                            inbox = "https://remote.example/inbox",
                            failedAtText = "諦めた時刻は不明",
                            errorText = "",
                        ),
                    ),
                    footer = FailedDeliveriesScreenUiState.Footer.Loading,
                ),
                listener = AndroidPreviewFailedDeliveriesListener,
            ),
        )
    }
}

@Preview
@Composable
private fun FailedDeliveriesContentEmptyPreview() {
    MaterialTheme {
        FailedDeliveriesContent(
            uiState = FailedDeliveriesScreenUiState(
                content = FailedDeliveriesScreenUiState.Content.Empty,
                listener = AndroidPreviewFailedDeliveriesListener,
            ),
        )
    }
}

private fun previewDelivery(
    listKey: String,
    kindText: String,
    inbox: String,
    failedAtText: String,
    errorText: String,
): FailedDeliveriesScreenUiState.Delivery = FailedDeliveriesScreenUiState.Delivery(
    listKey = listKey,
    kindText = kindText,
    inbox = inbox,
    failedAtText = failedAtText,
    errorText = errorText,
    listener = AndroidPreviewDeliveryListener,
)

private object AndroidPreviewDeliveryListener : FailedDeliveriesScreenUiState.DeliveryListener {
    override fun onClick() = Unit
}

private object AndroidPreviewFailedDeliveriesListener : FailedDeliveriesScreenUiState.Listener {
    override fun onClickBack() = Unit

    override fun onClickRetry() = Unit

    override fun onScrolledToEnd() = Unit

    override fun onClickRetryLoadMore() = Unit
}
