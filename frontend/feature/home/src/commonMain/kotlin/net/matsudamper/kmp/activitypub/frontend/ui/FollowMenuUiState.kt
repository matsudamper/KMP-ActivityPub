package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Immutable

/**
 * 購読先を選ぶプルダウン
 *
 * @param selectedText 今の購読先。購読していなければ未購読と出す
 */
data class FollowMenuUiState(
    val selectedText: String,
    val enabled: Boolean,
    val items: List<Item>,
) {
    data class Item(
        val text: String,
        val checkMarkVisible: Boolean,
        val listener: ItemListener,
    )

    @Immutable
    interface ItemListener {
        fun onClick()
    }
}
