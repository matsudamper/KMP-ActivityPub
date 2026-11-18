package net.matsudamper.kmp.activitypub.graphql.resolver

import java.net.URI
import java.time.Instant
import net.matsudamper.kmp.activitypub.graphql.DiContainer
import net.matsudamper.kmp.activitypub.graphql.model.QlActor
import net.matsudamper.kmp.activitypub.graphql.model.QlNotificationActor
import net.matsudamper.kmp.activitypub.graphql.model.QlNotificationGroup
import net.matsudamper.kmp.activitypub.graphql.model.QlNotificationKind
import net.matsudamper.kmp.activitypub.graphql.model.QlNotificationReaction
import net.matsudamper.kmp.activitypub.graphql.model.QlPostPoll
import net.matsudamper.kmp.activitypub.graphql.model.QlTimelinePost
import net.matsudamper.kmp.activitypub.logic.NotificationView
import net.matsudamper.kmp.activitypub.logic.PostCounts
import net.matsudamper.kmp.activitypub.logic.ProfileIconUrls
import net.matsudamper.kmp.activitypub.logic.memoized
import net.matsudamper.kmp.activitypub.repository.NotificationActor
import net.matsudamper.kmp.activitypub.repository.NotificationKind
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.shared.TimelinePostId

/**
 * このサーバーのユーザーのアイコンは、自分の投稿ごとに引き直さないよう、並べる投稿の分で覚えておく
 */
internal fun List<TimelinePost>.toQlTimelinePosts(diContainer: DiContainer): List<QlTimelinePost> {
    val localIconUrls = diContainer.profileIconUrls?.memoized()
    val countsByRef = diContainer.postCountService?.countsOf(map { it.noteRef }.toSet()).orEmpty()
    val pollsByRef = diContainer.pollService?.pollsOf(map { it.noteRef }.toSet()).orEmpty()
    val now = Instant.now()
    return map { post ->
        post.toQlTimelinePost(
            localIconUrls = localIconUrls,
            counts = countsByRef[post.noteRef],
            poll = pollsByRef[post.noteRef]?.toQlPostPoll(now),
            diContainer = diContainer,
        )
    }
}

private fun TimelinePost.toQlTimelinePost(
    localIconUrls: ProfileIconUrls?,
    counts: PostCounts?,
    poll: QlPostPoll?,
    diContainer: DiContainer,
): QlTimelinePost {
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
        boostUndone = boostUndone,
        sortedAt = Instant.ofEpochMilli(position.score).toString(),
        content = body.content,
        language = language?.toLanguageTag(),
        publishedAt = publishedAt.toString(),
        author = QlActor(
            actorUri = actorUri,
            username = username,
            host = actorUri?.let { URI(it).host },
            displayName = displayName ?: username,
            displayNameEmojis = actorEmojis.toQlCustomEmojis(diContainer),
            iconUrl = if (actorUri == null) {
                localIconUrls?.find(username)
            } else {
                actorIconUrl?.let { diContainer.remoteImageProxy?.proxyUrlOf(remoteUrl = it, updatedAt = actorUpdatedAt) }
            },
        ),
        summary = body.summary,
        sensitive = body.sensitive,
        linkUrls = body.linkUrls,
        mentions = body.mentions,
        images = body.images,
        videos = body.videos,
        emojis = createQlPostEmojis(noteRef = noteRef, contentHtml = contentHtml, remoteEmojis = emojis, diContainer = diContainer),
        // ブーストとして載せた投稿は他のアカウントの投稿のこともあり、ブーストされた元の投稿を消したり固定したりする場所ではない
        deletableNoteId = if (boostedBy == null) localPublicId else null,
        pinnableNoteId = if (boostedBy == null) localPublicId else null,
        favouriteCount = (favouriteCount ?: counts?.reportedFavouriteCount)?.toInt(),
        stamps = stamps.toQlPostStamps(diContainer),
        url = diContainer.noteShareUrls?.url(noteRef),
        likedByMe = likedByMe,
        boostedByMe = boostedByMe,
        reactionPostId = TimelinePostId(reactionMember),
        boostCount = (boostCount ?: counts?.reportedBoostCount)?.toInt(),
        boostedBy = boostedBy?.let { booster ->
            QlActor(
                actorUri = booster.actorUri,
                username = booster.username,
                host = booster.actorUri?.let { URI(it).host },
                displayName = booster.displayName ?: booster.username,
                displayNameEmojis = booster.emojis.toQlCustomEmojis(diContainer),
                iconUrl = if (booster.actorUri == null) {
                    localIconUrls?.find(booster.username)
                } else {
                    booster.iconUrl?.let { diContainer.remoteImageProxy?.proxyUrlOf(remoteUrl = it, updatedAt = booster.updatedAt) }
                },
            )
        },
        hasReplyTarget = inReplyTo != null,
        replyCount = counts?.replyCount?.toInt() ?: 0,
        stampCountsReceived = noteRef is TimelineNoteRef.Local,
        hasQuoteTarget = quoteUri != null,
        edited = editedAt != null,
        poll = poll,
    )
}

/**
 * 投稿は並べる分をまとめて引くので、呼ぶ側で作って渡す
 *
 * @param post [NotificationView.post] を変換したもの
 */
internal fun NotificationView.toQlNotificationGroup(post: QlTimelinePost?, localIconUrls: ProfileIconUrls?, diContainer: DiContainer): QlNotificationGroup = QlNotificationGroup(
    id = group.id,
    kind = when (group.kind) {
        NotificationKind.FOLLOW -> QlNotificationKind.FOLLOW
        NotificationKind.MENTION -> QlNotificationKind.MENTION
        NotificationKind.FAVOURITE -> QlNotificationKind.FAVOURITE
        NotificationKind.BOOST -> QlNotificationKind.BOOST
    },
    post = post,
    recentActors = recentEntries.map { entry ->
        QlNotificationActor(
            actor = entry.actor.toQlActor(localIconUrls, diContainer),
            reaction = entry.reaction?.let { reaction ->
                val remoteImageUrl = reaction.emojiImageUrl
                QlNotificationReaction(
                    emoji = reaction.emoji,
                    // 画像を中継できないときは :name: の文字で見せる
                    imageUrl = if (remoteImageUrl != null) {
                        diContainer.remoteImageProxy?.proxyUrlOf(remoteImageUrl)
                    } else {
                        reaction.localCustomEmojiPublicId?.let { diContainer.customEmojiUrls?.imageUrl(it) }
                    },
                )
            },
        )
    },
    actorCount = actorCount,
    createdAt = group.createdAt.toString(),
    updatedAt = group.updatedAt.toString(),
)

/**
 * 投稿をまとめて変換し、それぞれの通知に付ける
 */
internal fun List<NotificationView>.toQlNotificationGroups(diContainer: DiContainer): List<QlNotificationGroup> {
    val posts = mapNotNull { it.post }.distinctBy { it.position.member }.toQlTimelinePosts(diContainer).associateBy { it.id.value }
    val localIconUrls = diContainer.profileIconUrls?.memoized()
    return map { view ->
        val post = view.post?.let { post ->
            val mapped = posts.getValue(post.position.member)
            // メンションの投稿はこのサーバーの別のアカウントのものもあり、そこから消したり固定したりはできない
            if (diContainer.viewablePostService.isOwnedByViewer(view.group.accountId, post)) mapped else mapped.copy(deletableNoteId = null, pinnableNoteId = null)
        }
        view.toQlNotificationGroup(post, localIconUrls, diContainer)
    }
}

private fun NotificationActor.toQlActor(localIconUrls: ProfileIconUrls?, diContainer: DiContainer): QlActor {
    val updatedAt = updatedAt
    return QlActor(
        actorUri = actorUri,
        username = username,
        host = actorUri?.let { URI(it).host },
        displayName = displayName ?: username,
        displayNameEmojis = emojis.toQlCustomEmojis(diContainer),
        iconUrl = if (actorUri == null || updatedAt == null) {
            localIconUrls?.find(username)
        } else {
            iconUrl?.let { diContainer.remoteImageProxy?.proxyUrlOf(remoteUrl = it, updatedAt = updatedAt) }
        },
    )
}
