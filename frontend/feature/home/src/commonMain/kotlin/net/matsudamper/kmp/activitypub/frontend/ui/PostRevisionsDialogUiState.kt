package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Immutable

/**
 * 編集された投稿の版を、1 つずつ送って見るダイアログ
 */
data class PostRevisionsDialogUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data object Error : Content

        /**
         * @param positionText 何番目の版を出しているか
         * @param revisedAt この版になった年月日と時刻
         */
        data class Loaded(
            val positionText: String,
            val revisedAt: String,
            val body: TextWithEmojis,
            val images: List<PostItemUiState.Image>,
            val olderButtonEnabled: Boolean,
            val newerButtonEnabled: Boolean,
        ) : Content
    }

    @Immutable
    interface Listener {
        fun onClickOlder()

        fun onClickNewer()

        fun onClickRetry()

        fun onDismiss()
    }
}
