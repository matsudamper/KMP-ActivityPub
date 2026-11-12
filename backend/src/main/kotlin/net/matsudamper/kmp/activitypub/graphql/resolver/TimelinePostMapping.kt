package net.matsudamper.kmp.activitypub.graphql.resolver

import java.net.URI
import java.time.Instant
import net.matsudamper.kmp.activitypub.graphql.DiContainer
import net.matsudamper.kmp.activitypub.graphql.model.QlActor
import net.matsudamper.kmp.activitypub.graphql.model.QlNotification
import net.matsudamper.kmp.activitypub.graphql.model.QlNotificationKind
import net.matsudamper.kmp.activitypub.graphql.model.QlTimelinePost
import net.matsudamper.kmp.activitypub.repository.NotificationKind
import net.matsudamper.kmp.activitypub.repository.ReceivedNotification
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kmp.activitypub.shared.TimelinePostId

/**
 * 投稿ごとに固定を引くと件数分問い合わせることになるので、並べる投稿の分をまとめて引く
 */
internal fun List<TimelinePost>.toQlTimelinePosts(accountId: AccountId, diContainer: DiContainer): List<QlTimelinePost> {
    val pinnedNoteIds = diContainer.notePinService.pinnedAmong(mapNotNull { it.localPublicId }.toSet())
    return map { it.toQlTimelinePost(accountId, pinnedNoteIds, diContainer) }
}

private fun TimelinePost.toQlTimelinePost(accountId: AccountId, pinnedNoteIds: Set<PublicNoteId>, diContainer: DiContainer): QlTimelinePost {
    val body = createQlPostBody(
        contentHtml = contentHtml,
        summary = summary,
        sensitive = sensitive,
        images = images,
        linkUrlsEnabled = true,
        diContainer = diContainer,
    )
    return QlTimelinePost(
        id = TimelinePostId(position.member),
        deleted = deleted,
        sortedAt = Instant.ofEpochMilli(position.score).toString(),
        content = body.content,
        publishedAt = publishedAt.toString(),
        author = QlActor(
            username = username,
            host = actorUri?.let { URI(it).host },
            displayName = displayName ?: username,
            displayNameEmojis = actorEmojis.toQlCustomEmojis(diContainer),
            iconUrl = if (actorUri == null) {
                diContainer.profileIconUrls?.find(username)
            } else {
                actorIconUrl?.let { diContainer.remoteImageProxy?.proxyUrlOf(remoteUrl = it, updatedAt = actorUpdatedAt) }
            },
        ),
        summary = body.summary,
        sensitive = body.sensitive,
        linkUrls = body.linkUrls,
        images = body.images,
        emojis = emojis.toQlCustomEmojis(diContainer),
        // ブーストとして載せた投稿は他のアカウントの投稿のこともあり、ブーストされた元の投稿を消したり固定したりする場所ではない
        deletableNoteId = if (boostedBy == null) localPublicId?.value else null,
        pinnableNoteId = if (boostedBy == null) localPublicId else null,
        pinned = boostedBy == null && localPublicId in pinnedNoteIds,
        favouriteCount = favouriteCount?.toInt(),
        stamps = stamps.toQlPostStamps(diContainer),
        url = diContainer.noteShareUrls?.url(noteRef),
        likedByMe = likedByMe,
        boostedByMe = boostedByMe,
        reactionPostId = TimelinePostId(reactionMember),
        boostCount = boostCount?.toInt(),
        boostedBy = boostedBy?.let { booster ->
            QlActor(
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

internal fun ReceivedNotification.toQlNotification(post: QlTimelinePost, diContainer: DiContainer): QlNotification = QlNotification(
    id = id,
    kind = when (kind) {
        NotificationKind.FAVOURITE -> QlNotificationKind.FAVOURITE
        NotificationKind.BOOST -> QlNotificationKind.BOOST
    },
    actor = QlActor(
        username = actor.username,
        host = URI(actor.actorUri).host,
        displayName = actor.displayName ?: actor.username,
        displayNameEmojis = actor.emojis.toQlCustomEmojis(diContainer),
        iconUrl = actor.iconUrl?.let { diContainer.remoteImageProxy?.proxyUrlOf(remoteUrl = it, updatedAt = actor.updatedAt) },
    ),
    post = post,
    receivedAt = receivedAt.toString(),
)
