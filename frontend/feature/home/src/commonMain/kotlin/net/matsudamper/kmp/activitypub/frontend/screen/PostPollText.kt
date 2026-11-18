package net.matsudamper.kmp.activitypub.frontend.screen

import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserCustomEmoji
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostPoll
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

/**
 * 割合は Mastodon と同じく、1 つしか選べない投票は票の合計で、複数選べる投票は投票した人数で割る。
 * 複数選べる投票で人数が分からなければ、票の合計で割る
 *
 * @param emojis 選択肢に入るカスタム絵文字
 */
internal fun createPostPoll(poll: UserPostPoll, emojis: List<UserCustomEmoji>, now: Instant): PostItemUiState.Poll {
    val totalVotes = poll.options.sumOf { it.votesCount ?: 0 }
    val denominator = if (poll.multipleChoice) poll.votersCount ?: totalVotes else totalVotes
    return PostItemUiState.Poll(
        options = poll.options.map { option ->
            val votesCount = option.votesCount
            val ratio = if (votesCount == null || denominator <= 0) 0f else (votesCount.toFloat() / denominator).coerceIn(0f, 1f)
            PostItemUiState.PollOption(
                title = createTextWithEmojis(text = option.title, emojis = emojis),
                votesText = votesCount?.let { "${(ratio * 100).roundToInt()}%" },
                ratio = ratio,
            )
        },
        summaryText = listOfNotNull(
            poll.votersCount?.let { "${it}人が投票" },
            createPollDeadlineText(poll = poll, now = now),
            if (poll.multipleChoice) "複数選択" else null,
        ).joinToString(" · "),
    )
}

/**
 * 締め切りが分からなければ null
 */
private fun createPollDeadlineText(poll: UserPostPoll, now: Instant): String? {
    if (poll.expired) return "締め切り済み"
    val endsAt = poll.endsAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    val remaining = (endsAt - now).coerceAtLeast(Duration.ZERO)
    return when {
        remaining < 1.hours -> "あと${remaining.inWholeMinutes.coerceAtLeast(1)}分"
        remaining < 1.days -> "あと${remaining.inWholeHours}時間"
        else -> "あと${remaining.inWholeDays}日"
    }
}
