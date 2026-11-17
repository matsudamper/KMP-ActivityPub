package net.matsudamper.kmp.activitypub.frontend.screen.postrevisions

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.TextWithEmojis

data class PostRevisionsScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data class Error(
            val message: String,
        ) : Content

        /**
         * @param revisions 新しい順。最初が今の版
         */
        data class Loaded(
            val revisions: List<Revision>,
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
     * @param listKey 続きを読んで版が増えても、画像の横スクロールの位置などが別の版に移らないよう、一覧の中で版を見分ける
     * @param revisedAt この版になった年月日と時刻
     */
    data class Revision(
        val listKey: String,
        val revisedAt: String,
        val currentLabelVisible: Boolean,
        val body: TextWithEmojis,
        val images: List<PostItemUiState.Image>,
    )

    @Immutable
    interface Listener {
        fun onClickBack()

        fun onClickRetry()

        fun onScrolledToEnd()

        fun onClickRetryLoadMore()
    }
}
