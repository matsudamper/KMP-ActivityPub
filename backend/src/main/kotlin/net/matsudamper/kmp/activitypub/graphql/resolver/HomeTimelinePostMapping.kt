package net.matsudamper.kmp.activitypub.graphql.resolver

import java.net.URI
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimelinePost
import net.matsudamper.kmp.activitypub.logic.LinkPreviewService
import net.matsudamper.kmp.activitypub.logic.htmlToPlainText
import net.matsudamper.kmp.activitypub.repository.TimelinePost

/**
 * @param linkPreviewService DOMAIN が無いと null。そのときはカードにするリンクを返さない
 */
internal fun TimelinePost.toQlHomeTimelinePost(linkPreviewService: LinkPreviewService?): QlHomeTimelinePost =
    QlHomeTimelinePost(
        id = position.member,
        content = htmlToPlainText(contentHtml),
        publishedAt = publishedAt.toString(),
        username = username,
        host = actorUri?.let { URI(it).host },
        displayName = displayName ?: username,
        summary = summary,
        sensitive = sensitive,
        linkUrls = linkPreviewService?.links(contentHtml).orEmpty(),
    )
