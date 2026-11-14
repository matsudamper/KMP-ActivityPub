package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLikeState
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostBody
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostStamp
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserReaction
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserViewerReaction
import net.matsudamper.kmp.activitypub.frontend.logic.user.likeState
import net.matsudamper.kmp.activitypub.frontend.logic.user.toViewerReaction
import net.matsudamper.kmp.activitypub.frontend.screen.reaction.PickedReaction
import net.matsudamper.kmp.activitypub.frontend.screen.reaction.ReactionPickerDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ReactionConfirmDialogUiState

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

/**
 * 付けているリアクションを外すか付け替える前の確かめ
 *
 * @param current 確かめを出したときに付けていたもの。送れなかったときにここへ戻す
 * @param next 付け替える先。外すときは null
 */
internal data class ReactionConfirmation(
    val reactionPostId: String,
    val current: UserLikeState.Reacted,
    val next: UserReaction?,
)

/**
 * 絵文字を選んだときにすること
 */
internal sealed interface ReactionDecision {
    /** 確かめずにそのまま付ける */
    data class Apply(val reactionPostId: String, val current: UserLikeState, val reaction: UserReaction) : ReactionDecision

    data class Confirm(val confirmation: ReactionConfirmation) : ReactionDecision

    data object None : ReactionDecision
}

/**
 * ピッカーで選んだとき。付けているものと同じなら何もしない。別のものを付けていれば付け替えを確かめる
 */
internal fun decidePickedReaction(reactionPostId: String, current: UserLikeState, picked: UserReaction): ReactionDecision = when {
    current !is UserLikeState.Reacted -> ReactionDecision.Apply(reactionPostId, current, picked)
    current.reaction == picked.toViewerReaction() -> ReactionDecision.None
    else -> ReactionDecision.Confirm(ReactionConfirmation(reactionPostId, current, picked))
}

/**
 * 投稿のスタンプを押したとき。自分が付けている絵文字なら外すかを、別の絵文字なら付け替えるかを確かめる
 */
internal fun decideStampTap(reactionPostId: String, body: UserPostBody, stamp: UserPostStamp): ReactionDecision {
    val current = body.likeState()
    if (current is UserLikeState.Reacted && stamp.isReactionOf(current.reaction)) {
        return ReactionDecision.Confirm(ReactionConfirmation(reactionPostId, current, next = null))
    }
    val reaction = stamp.toReaction() ?: return ReactionDecision.None
    return decidePickedReaction(reactionPostId, current, reaction)
}

internal fun UserPostStamp.isReactionOf(reaction: UserViewerReaction): Boolean = emoji == reaction.emoji && imageUrl == reaction.imageUrl

internal fun ReactionConfirmation.toDialogUiState(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
): ReactionConfirmDialogUiState = ReactionConfirmDialogUiState(
    title = if (next == null) "リアクションを外す" else "リアクションを変更",
    confirmButtonText = if (next == null) "外す" else "変える",
    current = current.reaction.let { ReactionConfirmDialogUiState.Emoji(emoji = it.emoji, imageUrl = it.imageUrl) },
    next = next?.toViewerReaction()?.let { ReactionConfirmDialogUiState.Emoji(emoji = it.emoji, imageUrl = it.imageUrl) },
    listener = object : ReactionConfirmDialogUiState.Listener {
        override fun onConfirm() {
            onConfirm()
        }

        override fun onDismiss() {
            onDismiss()
        }
    },
)
