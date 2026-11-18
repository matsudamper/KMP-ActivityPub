package net.matsudamper.kmp.activitypub.frontend.screen.admin.unknownobjecttypes

import androidx.compose.runtime.Immutable

data class UnknownObjectTypesScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data object LoadFailed : Content

        data object Empty : Content

        /**
         * @param types 初めて見た新しい順
         */
        data class Loaded(
            val types: List<UnknownObjectType>,
            val footer: Footer,
        ) : Content
    }

    /**
     * 一覧の末尾。続きの読み込みの状態を出す
     */
    sealed interface Footer {
        data object None : Footer

        data object Loading : Footer

        data object LoadFailed : Footer
    }

    /**
     * @param listKey 続きを読んで行が増えても、同じ型と出どころを同じ行として扱う
     * @param sourceUrl 押すとブラウザで開く
     */
    data class UnknownObjectType(
        val listKey: String,
        val typeName: String,
        val sourceUrl: String,
        val seenText: String,
        val listener: UnknownObjectTypeListener,
    )

    @Immutable
    interface UnknownObjectTypeListener {
        fun onClickSourceUrl()
    }

    @Immutable
    interface Listener {
        fun onClickBack()

        fun onClickRetry()

        fun onScrolledToEnd()

        fun onClickRetryLoadMore()
    }
}
