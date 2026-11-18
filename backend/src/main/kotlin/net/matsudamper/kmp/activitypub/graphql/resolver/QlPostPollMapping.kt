package net.matsudamper.kmp.activitypub.graphql.resolver

import java.time.Instant
import net.matsudamper.kmp.activitypub.graphql.model.QlPostPoll
import net.matsudamper.kmp.activitypub.graphql.model.QlPostPollOption
import net.matsudamper.kmp.activitypub.repository.PollRecord

internal fun PollRecord.toQlPostPoll(now: Instant): QlPostPoll {
    val pollEndsAt = endsAt
    return QlPostPoll(
        options = options.map { option -> QlPostPollOption(title = option.name, votesCount = option.votesCount?.toInt()) },
        multipleChoice = multipleChoice,
        endsAt = pollEndsAt?.toString(),
        expired = pollEndsAt != null && !pollEndsAt.isAfter(now),
        votersCount = votersCount?.toInt(),
    )
}
