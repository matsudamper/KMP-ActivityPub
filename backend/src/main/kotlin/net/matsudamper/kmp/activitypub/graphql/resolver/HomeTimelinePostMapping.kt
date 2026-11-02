package net.matsudamper.kmp.activitypub.graphql.resolver

import java.net.URI
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimelinePost
import net.matsudamper.kmp.activitypub.logic.htmlToPlainText
import net.matsudamper.kmp.activitypub.repository.TimelinePost

internal fun TimelinePost.toQlHomeTimelinePost(): QlHomeTimelinePost =
    QlHomeTimelinePost(
        id = position.member,
        content = htmlToPlainText(contentHtml),
        publishedAt = publishedAt.toString(),
        username = username,
        host = actorUri?.let { URI(it).host },
        displayName = displayName ?: username,
        summary = summary,
        sensitive = sensitive,
    )
