package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLikeState
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserReaction
import net.matsudamper.kmp.activitypub.frontend.logic.user.toViewerReaction
import net.matsudamper.kmp.activitypub.frontend.screen.reaction.PickedReaction
import net.matsudamper.kmp.activitypub.frontend.screen.reaction.ReactionPickerDialogUiState

/**
 * リアクションのピッカーを開いている投稿。投稿を出す画面ごとに持つ
 *
 * @param current 開いたときに付けていたもの。送れなかったときにここへ戻す
 * @param requestKey 開くたびに変える。前に開いたときの検索やタブを持ち越さない
 */
internal data class ReactionPickerTarget(
    val reactionPostId: String,
    val current: UserLikeState,
    val requestKey: String,
)

internal fun ReactionPickerTarget.toDialogUiState(
    onPick: (UserReaction) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
): ReactionPickerDialogUiState = ReactionPickerDialogUiState(
    requestKey = requestKey,
    removeButtonVisible = current is UserLikeState.Reacted,
    listener = object : ReactionPickerDialogUiState.Listener {
        override fun onPick(reaction: PickedReaction) {
            onPick(
                when (reaction) {
                    is PickedReaction.Unicode -> UserReaction.Unicode(reaction.emoji)
                    is PickedReaction.Custom -> UserReaction.Custom(shortcode = reaction.shortcode, imageUrl = reaction.imageUrl)
                },
            )
        }

        override fun onClickRemove() {
            onRemove()
        }

        override fun onDismiss() {
            onDismiss()
        }
    },
)

/**
 * @param reaction null なら外す
 */
internal fun UserReaction?.toLikeState(): UserLikeState = if (this == null) UserLikeState.None else UserLikeState.Reacted(toViewerReaction())
