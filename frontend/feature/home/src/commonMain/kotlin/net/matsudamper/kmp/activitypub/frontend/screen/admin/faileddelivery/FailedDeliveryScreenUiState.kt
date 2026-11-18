package net.matsudamper.kmp.activitypub.frontend.screen.admin.faileddelivery

import androidx.compose.runtime.Immutable

data class FailedDeliveryScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data object LoadFailed : Content

        /**
         * @param fields 上から並べる項目
         */
        data class Loaded(
            val fields: List<Field>,
        ) : Content
    }

    data class Field(
        val label: String,
        val value: String,
    )

    @Immutable
    interface Listener {
        fun onClickBack()

        fun onClickRetry()
    }
}
