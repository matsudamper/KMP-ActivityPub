package net.matsudamper.kmp.activitypub.frontend.screen.reaction

import androidx.compose.runtime.Immutable

/**
 * 投稿を出す画面が持つ、ピッカーを開いているときの状態
 *
 * @param requestKey 開くたびに変わる。検索や選んだタブを前に開いたときから持ち越さない
 */
data class ReactionPickerDialogUiState(
    val requestKey: String,
    val removeButtonVisible: Boolean,
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onPick(reaction: PickedReaction)

        fun onClickRemove()

        fun onDismiss()
    }
}
