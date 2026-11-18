package net.matsudamper.kmp.activitypub.frontend.screen.admin.faileddeliveries

import androidx.compose.runtime.Immutable

data class FailedDeliveriesScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data class Error(
            val message: String,
        ) : Content

        data object Empty : Content

        /**
         * @param deliveries 新しく積んだ順
         */
        data class Loaded(
            val deliveries: List<Delivery>,
            val footer: Footer,
        ) : Content
    }

    /**
     * 一覧の末尾。続きの読み込みの状態を出す
     */
    sealed interface Footer {
        data object None : Footer

        data object Loading : Footer

        data class Error(
            val message: String,
        ) : Footer
    }

    /**
     * @param listKey 続きを読んで行が増えても、同じ配信を同じ行として扱う
     * @param errorText 最後に送れなかった理由。残っていなければ空
     */
    data class Delivery(
        val listKey: String,
        val kindText: String,
        val inbox: String,
        val failedAtText: String,
        val errorText: String,
        val listener: DeliveryListener,
    )

    @Immutable
    interface DeliveryListener {
        fun onClick()
    }

    @Immutable
    interface Listener {
        fun onClickBack()

        fun onClickRetry()

        fun onScrolledToEnd()

        fun onClickRetryLoadMore()
    }
}
