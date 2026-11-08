package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Immutable

/**
 * 購読ボタンと、押すと開く仕分け先のメニュー
 */
data class FollowMenuUiState(
    val buttonText: String,
    val buttonEnabled: Boolean,
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
