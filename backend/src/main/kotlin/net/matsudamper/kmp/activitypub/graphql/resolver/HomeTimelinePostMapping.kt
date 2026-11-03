package net.matsudamper.kmp.activitypub.graphql.resolver

import java.net.URI
import net.matsudamper.kmp.activitypub.graphql.DiContainer
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimelinePost
import net.matsudamper.kmp.activitypub.graphql.model.QlPostImage
import net.matsudamper.kmp.activitypub.logic.htmlToPlainText
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage

internal fun TimelinePost.toQlHomeTimelinePost(diContainer: DiContainer): QlHomeTimelinePost =
    QlHomeTimelinePost(
        id = position.member,
        content = htmlToPlainText(contentHtml),
        publishedAt = publishedAt.toString(),
        username = username,
        host = actorUri?.let { URI(it).host },
        displayName = displayName ?: username,
        summary = summary,
        sensitive = sensitive,
        // DOMAIN が無いと linkPreviewService は null。そのときはカードにするリンクを返さない
        linkUrls = diContainer.linkPreviewService?.links(contentHtml).orEmpty(),
        iconUrl = if (actorUri == null) {
            diContainer.profileIconUrls?.find(username)
        } else {
            actorIconUrl?.let { diContainer.remoteImageProxy?.proxyUrlOf(remoteUrl = it, updatedAt = actorUpdatedAt) }
        },
        images = images.toQlPostImages(diContainer),
    )

// Web では CORS を返さないサーバーの画像を読めないので、中継できない画像は返さない
internal fun List<TimelinePostImage>.toQlPostImages(diContainer: DiContainer): List<QlPostImage> = mapNotNull { image ->
    diContainer.remoteImageProxy?.proxyUrlOf(image.url)?.let { QlPostImage(url = it, description = image.description) }
}
