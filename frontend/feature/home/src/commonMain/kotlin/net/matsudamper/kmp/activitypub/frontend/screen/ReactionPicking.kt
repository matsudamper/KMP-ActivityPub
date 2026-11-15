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
 */
internal data class ReactionConfirmation(
    val reactionPostId: String,
    val current: UserLikeState.Reacted,
    val next: ReactionChange,
)

/**
 * 付けているリアクションをどうするか
 */
internal sealed interface ReactionChange {
    data object Remove : ReactionChange

    data class React(val reaction: UserReaction) : ReactionChange

    /** 絵文字の無いいいねに変える */
    data object PlainLike : ReactionChange
}

/**
 * いいねを押したとき。リアクションを付けていれば、いいねに変えるかを確かめる
 */
internal fun decideLikeTap(reactionPostId: String, body: UserPostBody): ReactionConfirmation? {
    val current = body.likeState() as? UserLikeState.Reacted ?: return null
    return ReactionConfirmation(reactionPostId, current, ReactionChange.PlainLike)
}

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
    else -> ReactionDecision.Confirm(ReactionConfirmation(reactionPostId, current, ReactionChange.React(picked)))
}

/**
 * 投稿のスタンプを押したとき。自分が付けている絵文字なら外すかを、別の絵文字なら付け替えるかを確かめる
 */
internal fun decideStampTap(reactionPostId: String, body: UserPostBody, stamp: UserPostStamp): ReactionDecision {
    val current = body.likeState()
    if (current is UserLikeState.Reacted && stamp.isReactionOf(current.reaction)) {
        return ReactionDecision.Confirm(ReactionConfirmation(reactionPostId, current, ReactionChange.Remove))
    }
    val reaction = stamp.toReaction() ?: return ReactionDecision.None
    return decidePickedReaction(reactionPostId, current, reaction)
}

internal fun UserPostStamp.isReactionOf(reaction: UserViewerReaction): Boolean = emoji == reaction.emoji && imageUrl == reaction.imageUrl

internal fun ReactionConfirmation.toDialogUiState(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
): ReactionConfirmDialogUiState = ReactionConfirmDialogUiState(
    title = if (next == ReactionChange.Remove) "リアクションを外す" else "リアクションを変更",
    confirmButtonText = if (next == ReactionChange.Remove) "外す" else "変える",
    current = current.reaction.let { ReactionConfirmDialogUiState.Emoji(emoji = it.emoji, imageUrl = it.imageUrl) },
    next = when (next) {
        ReactionChange.Remove -> null
        is ReactionChange.React -> next.reaction.toViewerReaction().let { ReactionConfirmDialogUiState.Next.ToEmoji(ReactionConfirmDialogUiState.Emoji(emoji = it.emoji, imageUrl = it.imageUrl)) }
        ReactionChange.PlainLike -> ReactionConfirmDialogUiState.Next.ToLike
    },
    listener = object : ReactionConfirmDialogUiState.Listener {
        override fun onConfirm() {
            onConfirm()
        }

        override fun onDismiss() {
            onDismiss()
        }
    },
)
