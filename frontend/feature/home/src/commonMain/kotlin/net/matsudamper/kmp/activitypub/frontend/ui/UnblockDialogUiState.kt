package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Immutable

/**
 * @param message 何のブロックを解除するか
 */
data class UnblockDialogUiState(
    val message: String,
    val buttonsEnabled: Boolean,
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onClickUnblock()

        fun onDismiss()
    }
}
