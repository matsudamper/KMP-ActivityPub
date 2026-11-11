package net.matsudamper.kmp.activitypub.graphql.resolver

import java.net.URI
import net.matsudamper.kmp.activitypub.graphql.DiContainer
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimelinePost
import net.matsudamper.kmp.activitypub.graphql.model.QlPostBooster
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.TimelinePostId

internal fun TimelinePost.toQlHomeTimelinePost(accountId: AccountId, diContainer: DiContainer): QlHomeTimelinePost {
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
        emojis = emojis.toQlCustomEmojis(diContainer),
        displayNameEmojis = actorEmojis.toQlCustomEmojis(diContainer),
        deletableNoteId = localPublicId?.value,
        favouriteCount = favouriteCount?.toInt(),
        stamps = stamps.toQlPostStamps(diContainer),
        url = diContainer.noteShareUrls?.url(noteRef),
        likedByMe = likedByMe,
        boostedByMe = boostedByMe,
        reactionPostId = TimelinePostId(reactionMember),
        boostCount = boostCount?.toInt(),
        boostedBy = boostedBy?.let { booster ->
            QlPostBooster(
                username = booster.username,
                host = booster.actorUri?.let { URI(it).host },
                displayName = booster.displayName ?: booster.username,
                displayNameEmojis = booster.emojis.toQlCustomEmojis(diContainer),
                iconUrl = if (booster.actorUri == null) {
                    diContainer.profileIconUrls?.find(booster.username)
                } else {
                    booster.iconUrl?.let { diContainer.remoteImageProxy?.proxyUrlOf(remoteUrl = it, updatedAt = booster.updatedAt) }
                },
            )
        },
        followingGroupId = diContainer.followingGroupService.groupIdOfPost(accountId, this),
    )
}
