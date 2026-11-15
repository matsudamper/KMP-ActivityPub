package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Immutable

/**
 * リアクションを外すか付け替える前に確かめるダイアログ
 *
 * @param next 付け替える先。外すときは null で、今のリアクションだけを出す
 */
data class ReactionConfirmDialogUiState(
    val title: String,
    val confirmButtonText: String,
    val current: Emoji,
    val next: Next?,
    val listener: Listener,
) {
    /**
     * @param imageUrl カスタム絵文字の画像。null なら [emoji] の文字で見せる
     */
    data class Emoji(
        val emoji: String,
        val imageUrl: String?,
    )

    sealed interface Next {
        data class ToEmoji(val emoji: Emoji) : Next

        /** 絵文字の無いいいね */
        data object ToLike : Next
    }

    @Immutable
    interface Listener {
        fun onConfirm()

        fun onDismiss()
    }
}
