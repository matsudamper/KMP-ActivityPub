package net.matsudamper.kmp.activitypub.frontend.screen.admin.faileddelivery

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun FailedDeliveryContentPreview() {
    MaterialTheme {
        FailedDeliveryContent(
            uiState = FailedDeliveryScreenUiState(
                content = FailedDeliveryScreenUiState.Content.Loaded(
                    fields = listOf(
                        FailedDeliveryScreenUiState.Field(label = "種別", value = "フォローの承認"),
                        FailedDeliveryScreenUiState.Field(label = "送り主", value = "@alice"),
                        FailedDeliveryScreenUiState.Field(label = "宛先の inbox", value = "https://remote.example/users/bob/inbox"),
                        FailedDeliveryScreenUiState.Field(label = "送ろうとした回数", value = "12 回"),
                        FailedDeliveryScreenUiState.Field(label = "積んだ時刻", value = "2026/11/01 21:00"),
                        FailedDeliveryScreenUiState.Field(label = "諦めた時刻", value = "2026/11/08 20:12"),
                        FailedDeliveryScreenUiState.Field(
                            label = "最後に送れなかった理由",
                            value = "java.net.ConnectException: 相手のサーバーにつながらなかった",
                        ),
                    ),
                ),
                listener = AndroidPreviewFailedDeliveryListener,
            ),
        )
    }
}

private object AndroidPreviewFailedDeliveryListener : FailedDeliveryScreenUiState.Listener {
    override fun onClickBack() = Unit

    override fun onClickRetry() = Unit
}
