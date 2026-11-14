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
    val next: Emoji?,
    val listener: Listener,
) {
    /**
     * @param imageUrl カスタム絵文字の画像。null なら [emoji] の文字で見せる
     */
    data class Emoji(
        val emoji: String,
        val imageUrl: String?,
    )

    @Immutable
    interface Listener {
        fun onConfirm()

        fun onDismiss()
    }
}
