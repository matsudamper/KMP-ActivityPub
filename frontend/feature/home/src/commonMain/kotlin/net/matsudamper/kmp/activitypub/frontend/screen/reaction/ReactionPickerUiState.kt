package net.matsudamper.kmp.activitypub.frontend.screen.reaction

import androidx.compose.runtime.Immutable

data class ReactionPickerUiState(
    val searchQuery: String,
    val selectedTab: Tab,
    val content: Content,
    val listener: Listener,
) {
    enum class Tab {
        CustomEmoji,
        Unicode,
    }

    sealed interface Content {
        data object Loading : Content

        data class Error(val message: String) : Content

        /**
         * @param emptyMessage 1 つも出せないときに出す文言。出せるものがあれば null
         */
        data class CustomEmojis(
            val items: List<CustomEmojiItem>,
            val emptyMessage: String?,
        ) : Content

        data class UnicodeEmojis(
            val sections: List<UnicodeSection>,
            val emptyMessageVisible: Boolean,
        ) : Content
    }

    data class CustomEmojiItem(
        val shortcode: String,
        val imageUrl: String,
        val listener: ItemListener,
    )

    /**
     * @param title 検索しているときは分類に分けないので null
     */
    data class UnicodeSection(
        val title: String?,
        val items: List<UnicodeEmojiItem>,
    )

    data class UnicodeEmojiItem(
        val emoji: String,
        val listener: ItemListener,
    )

    @Immutable
    interface ItemListener {
        fun onClick()
    }

    @Immutable
    interface Listener {
        fun onChangeSearchQuery(value: String)

        fun onSelectTab(tab: Tab)

        fun onClickRetry()
    }
}
