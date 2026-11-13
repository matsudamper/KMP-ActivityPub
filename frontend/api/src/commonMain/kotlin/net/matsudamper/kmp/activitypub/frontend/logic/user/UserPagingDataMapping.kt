package net.matsudamper.kmp.activitypub.frontend.logic.user

import net.matsudamper.kmp.activitypub.frontend.graphql.FollowerUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.FollowingUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeScreenSearchUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.NotificationsQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.TimelineQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.ActorFields
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.ActorListFields
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.NotificationFields
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.TimelinePostFields
import net.matsudamper.kmp.activitypub.frontend.graphql.type.NotificationKind

fun TimelineQuery.Data.toUserHomeTimelineResult(): UserHomeTimelineResult {
    val timeline = userInfo?.timeline ?: return UserHomeTimelineResult.NotLoggedIn
    return UserHomeTimelineResult.Success(
        posts = timeline.posts.map { it.timelinePostFields.toPost() },
        nextCursor = timeline.nextCursor,
    )
}

fun NotificationsQuery.Data.toUserNotificationsResult(): UserNotificationsResult {
    val page = userInfo?.notifications ?: return UserNotificationsResult.NotLoggedIn
    return UserNotificationsResult.Success(
        notifications = page.notifications.mapNotNull { it.notificationFields.toNotification() },
        nextCursor = page.nextCursor,
    )
}

internal fun TimelinePostFields.toPost(): UserHomeTimelineResult.Post {
    val authorFields = author.actorFields
    return UserHomeTimelineResult.Post(
        id = id,
        deleted = deleted,
        boostUndone = boostUndone,
        sortedAt = sortedAt,
        username = authorFields.username,
        host = authorFields.host,
        displayName = authorFields.displayName,
        displayNameEmojis = authorFields.displayNameEmojis.map { emoji -> UserCustomEmoji(name = emoji.name, imageUrl = emoji.imageUrl) },
        iconUrl = authorFields.iconUrl,
        body = UserPostBody(
            content = content,
            publishedAt = publishedAt,
            summary = summary,
            sensitive = sensitive,
            linkUrls = linkUrls,
            images = images.map { image -> UserPostImage(url = image.url, thumbnailUrl = image.thumbnailUrl, description = image.description) },
            favouriteCount = favouriteCount,
            boostCount = boostCount,
            stamps = stamps.map { stamp -> UserPostStamp(emoji = stamp.emoji, imageUrl = stamp.imageUrl, count = stamp.count) },
            emojis = emojis.map { emoji -> UserCustomEmoji(name = emoji.name, imageUrl = emoji.imageUrl) },
            shareUrl = url,
            likedByMe = likedByMe,
            boostedByMe = boostedByMe,
        ),
        boostedBy = boostedBy?.actorFields?.toActor(),
        reactionPostId = reactionPostId,
        deletableNoteId = deletableNoteId,
        pinnableNoteId = pinnableNoteId,
        pinned = pinned,
        followingGroupId = followingGroupId,
    )
}

/**
 * @return このアプリが知らない種類の通知なら null
 */
internal fun NotificationFields.toNotification(): UserNotificationsResult.Notification? {
    val notificationKind = when (kind) {
        NotificationKind.FAVOURITE -> UserNotificationsResult.Kind.Favourite
        NotificationKind.BOOST -> UserNotificationsResult.Kind.Boost
        NotificationKind.UNKNOWN__ -> return null
    }
    return UserNotificationsResult.Notification(
        id = id,
        kind = notificationKind,
        actor = actor.actorFields.toActor(),
        post = post.timelinePostFields.toPost(),
        receivedAt = receivedAt,
    )
}

private fun ActorFields.toActor(): UserHomeTimelineResult.Actor = UserHomeTimelineResult.Actor(
    username = username,
    host = host,
    displayName = displayName,
    displayNameEmojis = displayNameEmojis.map { emoji -> UserCustomEmoji(name = emoji.name, imageUrl = emoji.imageUrl) },
    iconUrl = iconUrl,
)

private fun UserHomeTimelineResult.Post.toFields(): TimelinePostFields = TimelinePostFields(
    __typename = "TimelinePost",
    id = id,
    deleted = deleted,
    boostUndone = boostUndone,
    sortedAt = sortedAt,
    content = body.content,
    publishedAt = body.publishedAt,
    author = TimelinePostFields.Author(
        __typename = "Actor",
        actorFields = ActorFields(
            __typename = "Actor",
            username = username,
            host = host,
            displayName = displayName,
            displayNameEmojis = displayNameEmojis.map { emoji -> ActorFields.DisplayNameEmoji(__typename = "CustomEmoji", name = emoji.name, imageUrl = emoji.imageUrl) },
            iconUrl = iconUrl,
        ),
    ),
    summary = body.summary,
    sensitive = body.sensitive,
    linkUrls = body.linkUrls,
    images = body.images.map { image ->
        TimelinePostFields.Image(__typename = "PostImage", url = image.url, thumbnailUrl = image.thumbnailUrl, description = image.description)
    },
    emojis = body.emojis.map { emoji -> TimelinePostFields.Emoji(__typename = "CustomEmoji", name = emoji.name, imageUrl = emoji.imageUrl) },
    deletableNoteId = deletableNoteId,
    pinnableNoteId = pinnableNoteId,
    pinned = pinned,
    favouriteCount = body.favouriteCount,
    boostCount = body.boostCount,
    boostedBy = boostedBy?.let { booster -> TimelinePostFields.BoostedBy(__typename = "Actor", actorFields = booster.toFields()) },
    stamps = body.stamps.map { stamp -> TimelinePostFields.Stamp(__typename = "PostStamp", emoji = stamp.emoji, imageUrl = stamp.imageUrl, count = stamp.count) },
    url = body.shareUrl,
    likedByMe = body.likedByMe == true,
    boostedByMe = body.boostedByMe,
    reactionPostId = reactionPostId,
    followingGroupId = followingGroupId,
)

private fun UserHomeTimelineResult.Actor.toFields(): ActorFields = ActorFields(
    __typename = "Actor",
    username = username,
    host = host,
    displayName = displayName,
    displayNameEmojis = displayNameEmojis.map { emoji -> ActorFields.DisplayNameEmoji(__typename = "CustomEmoji", name = emoji.name, imageUrl = emoji.imageUrl) },
    iconUrl = iconUrl,
)

private fun UserNotificationsResult.Notification.toFields(): NotificationFields = NotificationFields(
    __typename = "Notification",
    id = id,
    kind = when (kind) {
        UserNotificationsResult.Kind.Favourite -> NotificationKind.FAVOURITE
        UserNotificationsResult.Kind.Boost -> NotificationKind.BOOST
    },
    actor = NotificationFields.Actor(__typename = "Actor", actorFields = actor.toFields()),
    post = NotificationFields.Post(__typename = "TimelinePost", timelinePostFields = post.toFields()),
    receivedAt = receivedAt,
)

/**
 * 取り直した 1 ページ目を、読み込み済みの投稿の先頭へつなぐ。
 * 1 ページ目の最後の投稿が読み込み済みに無いと、その間の投稿が抜けるので null
 */
fun TimelineQuery.Data.withFirstPage(firstPage: TimelineQuery.Data): TimelineQuery.Data? {
    val loadedTimeline = userInfo?.timeline ?: return null
    val firstPageInfo = firstPage.userInfo ?: return null
    val firstPageTimeline = firstPageInfo.timeline ?: return null
    val lastFirstPagePostId = firstPageTimeline.posts.lastOrNull()?.timelinePostFields?.id ?: return null
    val joinIndex = loadedTimeline.posts.indexOfFirst { it.timelinePostFields.id == lastFirstPagePostId }.takeIf { it >= 0 } ?: return null
    return firstPage.copy(
        userInfo = firstPageInfo.copy(
            timeline = firstPageTimeline.copy(
                posts = firstPageTimeline.posts + loadedTimeline.posts.drop(joinIndex + 1),
                nextCursor = loadedTimeline.nextCursor,
            ),
        ),
    )
}

/**
 * 続きのページを後ろにつなぐ。どちらかが読めていなければ null
 */
fun NotificationsQuery.Data.withNextPage(page: NotificationsQuery.Data): NotificationsQuery.Data? {
    val beforeUserInfo = userInfo ?: return null
    val pageNotifications = page.userInfo?.notifications ?: return null
    val beforeNotifications = beforeUserInfo.notifications ?: return null
    return copy(
        userInfo = beforeUserInfo.copy(
            notifications = beforeNotifications.copy(
                notifications = beforeNotifications.notifications + pageNotifications.notifications,
                nextCursor = pageNotifications.nextCursor,
            ),
        ),
    )
}

/**
 * 取り直した 1 ページ目を、読み込み済みの通知の先頭へつなぐ。
 * 1 ページ目の最後の通知が読み込み済みに無いと、その間の通知が抜けるので null
 */
fun NotificationsQuery.Data.withFirstPage(firstPage: NotificationsQuery.Data): NotificationsQuery.Data? {
    val loadedPage = userInfo?.notifications ?: return null
    val firstPageInfo = firstPage.userInfo ?: return null
    val firstPageNotifications = firstPageInfo.notifications ?: return null
    val lastFirstPageId = firstPageNotifications.notifications.lastOrNull()?.notificationFields?.id ?: return null
    val joinIndex = loadedPage.notifications.indexOfFirst { it.notificationFields.id == lastFirstPageId }.takeIf { it >= 0 } ?: return null
    return firstPage.copy(
        userInfo = firstPageInfo.copy(
            notifications = firstPageNotifications.copy(
                notifications = firstPageNotifications.notifications + loadedPage.notifications.drop(joinIndex + 1),
                nextCursor = loadedPage.nextCursor,
            ),
        ),
    )
}

/**
 * 読み直した 1 ページ目で、読み込み済みの内容を置き換える。
 * 読み直しの結果は要求した時点のものなので、その間に購読で届いた投稿と削除は、読み込み済みの内容から引き継ぐ
 *
 * @param keepLoadedPages 読み込み済みの続きのページを残すか。1 ページ目とつなげなければ残さない
 * @param arrivedPostIds 読み直しを始めてから購読で届いた [UserHomeTimelineResult.Post.id]
 * @param removals 読み直しを始めてから購読で削除済みか取り消し済みになった行
 */
fun TimelineQuery.Data.refreshedWith(
    firstPage: TimelineQuery.Data,
    keepLoadedPages: Boolean,
    arrivedPostIds: Set<String>,
    removals: PostRemovals,
): TimelineQuery.Data {
    val joined = (if (keepLoadedPages) withFirstPage(firstPage) else null) ?: firstPage
    val info = joined.userInfo ?: return joined
    val timeline = info.timeline ?: return joined
    val arrived = userInfo?.timeline?.posts.orEmpty().filter { it.timelinePostFields.id in arrivedPostIds }
    return joined
        .copy(userInfo = info.copy(timeline = timeline.copy(posts = (arrived + timeline.posts).distinctBy { it.timelinePostFields.id })))
        .withPosts { it.withRemovals(removals) }
}

/**
 * 読み直した 1 ページ目で、読み込み済みの通知を置き換える。[TimelineQuery.Data.refreshedWith] の通知版
 *
 * @param arrivedNotificationIds 読み直しを始めてから購読で届いた [UserNotificationsResult.Notification.id]
 */
fun NotificationsQuery.Data.refreshedWith(
    firstPage: NotificationsQuery.Data,
    keepLoadedPages: Boolean,
    arrivedNotificationIds: Set<String>,
    removals: PostRemovals,
): NotificationsQuery.Data {
    val joined = (if (keepLoadedPages) withFirstPage(firstPage) else null) ?: firstPage
    val info = joined.userInfo ?: return joined
    val page = info.notifications ?: return joined
    val arrived = userInfo?.notifications?.notifications.orEmpty().filter { it.notificationFields.id in arrivedNotificationIds }
    return joined
        .copy(
            userInfo = info.copy(
                notifications = page.copy(notifications = (arrived + page.notifications).distinctBy { it.notificationFields.id }),
            ),
        )
        .withPosts { it.withRemovals(removals) }
}

/**
 * 購読で届いた投稿を、取得し直さずに先頭へ足す。[posts] は新しい順
 */
fun TimelineQuery.Data.withArrivedPosts(posts: List<UserHomeTimelineResult.Post>): TimelineQuery.Data {
    val info = userInfo ?: return this
    val timeline = info.timeline ?: return this
    val arrived = posts.map { TimelineQuery.Post(__typename = "TimelinePost", timelinePostFields = it.toFields()) }
    return copy(userInfo = info.copy(timeline = timeline.copy(posts = (arrived + timeline.posts).distinctBy { it.timelinePostFields.id })))
}

/**
 * 購読で届いた通知を、取得し直さずに先頭へ足す。[notifications] は新しい順
 */
fun NotificationsQuery.Data.withArrivedNotifications(notifications: List<UserNotificationsResult.Notification>): NotificationsQuery.Data {
    val info = userInfo ?: return this
    val page = info.notifications ?: return this
    val arrived = notifications.map { NotificationsQuery.Notification(__typename = "Notification", notificationFields = it.toFields()) }
    return copy(
        userInfo = info.copy(
            notifications = page.copy(notifications = (arrived + page.notifications).distinctBy { it.notificationFields.id }),
        ),
    )
}

/**
 * 並んでいる投稿を、取得し直さずに書き換える。いいねや固定など、手元で変えた結果を反映するのに使う
 */
fun TimelineQuery.Data.withPosts(transform: (TimelinePostFields) -> TimelinePostFields): TimelineQuery.Data {
    val info = userInfo ?: return this
    val timeline = info.timeline ?: return this
    return copy(
        userInfo = info.copy(
            timeline = timeline.copy(posts = timeline.posts.map { post -> post.copy(timelinePostFields = transform(post.timelinePostFields)) }),
        ),
    )
}

/**
 * 通知に付いた投稿を、取得し直さずに書き換える
 */
fun NotificationsQuery.Data.withPosts(transform: (TimelinePostFields) -> TimelinePostFields): NotificationsQuery.Data {
    val info = userInfo ?: return this
    val page = info.notifications ?: return this
    return copy(
        userInfo = info.copy(
            notifications = page.copy(
                notifications = page.notifications.map { notification ->
                    val fields = notification.notificationFields
                    notification.copy(
                        notificationFields = fields.copy(post = fields.post.copy(timelinePostFields = transform(fields.post.timelinePostFields))),
                    )
                },
            ),
        ),
    )
}

/**
 * 削除済みか取り消し済みになった行に印を付ける。読み込んでいない行は何もしない
 */
fun TimelinePostFields.withRemovals(removals: PostRemovals): TimelinePostFields = copy(
    deleted = deleted || id in removals.deletedPostIds,
    boostUndone = boostUndone || id in removals.boostUndonePostIds,
)

/**
 * いいねを変えた結果を反映する。
 * 同じ投稿が元の投稿とブーストの両方で並ぶことがあり、いいねは元の投稿に付くので、[reactionPostId] が同じものをまとめて切り替える
 */
fun TimelinePostFields.withLiked(reactionPostId: String, liked: Boolean): TimelinePostFields {
    if (this.reactionPostId != reactionPostId || likedByMe == liked) return this
    // 数は自分のいいねの分だけ増減させる。他の人のいいねは取り直すまで分からない
    return copy(
        likedByMe = liked,
        favouriteCount = favouriteCount?.let { count -> if (liked) count + 1 else (count - 1).coerceAtLeast(0) },
    )
}

/**
 * ブーストを変えた結果を反映する。
 * 同じ投稿が元の投稿とブーストの両方で並ぶことがあり、ブーストは元の投稿に付くので、[reactionPostId] が同じものをまとめて切り替える
 */
fun TimelinePostFields.withBoosted(reactionPostId: String, boosted: Boolean): TimelinePostFields {
    if (this.reactionPostId != reactionPostId || boostedByMe == null || boostedByMe == boosted) return this
    return copy(
        boostedByMe = boosted,
        boostCount = boostCount?.let { count -> if (boosted) count + 1 else (count - 1).coerceAtLeast(0) },
    )
}

/**
 * 固定を変えた結果を反映する
 */
fun TimelinePostFields.withPinned(pinnableNoteId: String, pinned: Boolean): TimelinePostFields =
    if (this.pinnableNoteId == pinnableNoteId) copy(pinned = pinned) else this

/**
 * 消した投稿を、取得し直さずに外す
 */
fun TimelineQuery.Data.withoutNote(deletableNoteId: String): TimelineQuery.Data {
    val info = userInfo ?: return this
    val timeline = info.timeline ?: return this
    return copy(userInfo = info.copy(timeline = timeline.copy(posts = timeline.posts.filterNot { it.timelinePostFields.deletableNoteId == deletableNoteId })))
}

/**
 * 消した投稿への通知を、取得し直さずに外す
 */
fun NotificationsQuery.Data.withoutNote(deletableNoteId: String): NotificationsQuery.Data {
    val info = userInfo ?: return this
    val page = info.notifications ?: return this
    return copy(
        userInfo = info.copy(
            notifications = page.copy(
                notifications = page.notifications.filterNot { it.notificationFields.post.timelinePostFields.deletableNoteId == deletableNoteId },
            ),
        ),
    )
}

fun HomeScreenSearchUsersQuery.Data.toUserSearchResult(): UserSearchResult.Success {
    return UserSearchResult.Success(
        users = searchUsers.users.map {
            UserSearchResult.User(
                actorUri = it.actorUri,
                following = it.following,
                followingGroupId = it.followingGroupId,
                username = it.username,
                host = it.host,
                isLocal = it.isLocal,
                displayName = it.displayName,
                displayNameEmojis = it.displayNameEmojis.map { emoji -> UserCustomEmoji(name = emoji.name, imageUrl = emoji.imageUrl) },
                iconUrl = it.iconUrl,
            )
        },
        nextCursor = searchUsers.nextCursor,
    )
}

/**
 * フォロー状態を変えた結果を、取得し直さずに反映する
 */
fun HomeScreenSearchUsersQuery.Data.withFollowing(change: UserApi.FollowingChange): HomeScreenSearchUsersQuery.Data {
    return copy(
        searchUsers = searchUsers.copy(
            users = searchUsers.users.map { if (it.actorUri == change.actorUri) it.copy(following = change.following, followingGroupId = change.groupId) else it },
        ),
    )
}

fun FollowingUsersQuery.Data.toUserFollowListResult(): UserFollowListResult {
    return followingUsers?.actorListFields?.toUserFollowListResult() ?: UserFollowListResult.NotLoggedIn
}

fun FollowerUsersQuery.Data.toUserFollowListResult(): UserFollowListResult {
    return followerUsers?.actorListFields?.toUserFollowListResult() ?: UserFollowListResult.NotLoggedIn
}

/**
 * フォロー状態を変えた結果を、取得し直さずに反映する
 */
fun FollowingUsersQuery.Data.withFollowing(change: UserApi.FollowingChange): FollowingUsersQuery.Data {
    val list = followingUsers ?: return this
    return copy(followingUsers = list.copy(actorListFields = list.actorListFields.withFollowing(change)))
}

/**
 * フォロー状態を変えた結果を、取得し直さずに反映する
 */
fun FollowerUsersQuery.Data.withFollowing(change: UserApi.FollowingChange): FollowerUsersQuery.Data {
    val list = followerUsers ?: return this
    return copy(followerUsers = list.copy(actorListFields = list.actorListFields.withFollowing(change)))
}

private fun ActorListFields.withFollowing(change: UserApi.FollowingChange): ActorListFields {
    return copy(users = users.map { if (it.actorUri == change.actorUri) it.copy(following = change.following, followingGroupId = change.groupId) else it })
}

private fun ActorListFields.toUserFollowListResult(): UserFollowListResult.Success {
    return UserFollowListResult.Success(
        users = users.map {
            UserFollowListResult.User(
                actorUri = it.actorUri,
                following = it.following,
                followingGroupId = it.followingGroupId,
                username = it.username,
                host = it.host,
                displayName = it.displayName,
                displayNameEmojis = it.displayNameEmojis.map { emoji -> UserCustomEmoji(name = emoji.name, imageUrl = emoji.imageUrl) },
                iconUrl = it.iconUrl,
            )
        },
        nextCursor = nextCursor,
    )
}
