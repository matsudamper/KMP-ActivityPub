package net.matsudamper.kmp.activitypub.graphql.resolver

import java.net.URI
import net.matsudamper.kmp.activitypub.graphql.DiContainer
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelinePost

internal fun TimelinePost.toQlHomeTimelinePost(diContainer: DiContainer): QlHomeTimelinePost {
    val body = createQlPostBody(
        contentHtml = contentHtml,
        summary = summary,
        sensitive = sensitive,
        images = images,
        linkUrlsEnabled = true,
        diContainer = diContainer,
    )
    return QlHomeTimelinePost(
        id = position.member,
        content = body.content,
        publishedAt = publishedAt.toString(),
        username = username,
        host = actorUri?.let { URI(it).host },
        displayName = displayName ?: username,
        summary = body.summary,
        sensitive = body.sensitive,
        linkUrls = body.linkUrls,
        iconUrl = if (actorUri == null) {
            diContainer.profileIconUrls?.find(username)
        } else {
            actorIconUrl?.let { diContainer.remoteImageProxy?.proxyUrlOf(remoteUrl = it, updatedAt = actorUpdatedAt) }
        },
        images = body.images,
        deletableNoteId = localPublicId?.value,
        favouriteCount = favouriteCount?.toInt(),
        stamps = stamps.toQlPostStamps(diContainer),
        url = diContainer.noteShareUrls?.url(noteRef),
        likedByMe = likedByMe,
    )
}
