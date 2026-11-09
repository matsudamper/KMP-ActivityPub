package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Immutable

/**
 * 購読ボタンと、押すと開く購読先のメニュー
 */
data class FollowMenuUiState(
    val button: Button,
    val enabled: Boolean,
    val items: List<Item>,
) {
    sealed interface Button {
        /**
         * 購読していない相手。目立たせて購読を促す
         */
        data class Follow(
            val text: String,
        ) : Button

        /**
         * @param destinationName 今の購読先
         */
        data class Following(
            val destinationName: String,
        ) : Button
    }

    sealed interface Item {
        val text: String
        val listener: ItemListener

        data class Destination(
            override val text: String,
            val selected: Boolean,
            override val listener: ItemListener,
        ) : Item

        data class Unfollow(
            override val text: String,
            override val listener: ItemListener,
        ) : Item

        data class AddDestination(
            override val text: String,
            override val listener: ItemListener,
        ) : Item
    }

    @Immutable
    interface ItemListener {
        fun onClick()
    }
}
