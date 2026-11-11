package net.matsudamper.kmp.activitypub.frontend.logic.user

import net.matsudamper.kmp.activitypub.frontend.graphql.FollowerUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.FollowingUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeScreenSearchUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeTimelineQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.ActorListFields

fun HomeTimelineQuery.Data.toUserHomeTimelineResult(): UserHomeTimelineResult {
    val timeline = userInfo?.homeTimeline ?: return UserHomeTimelineResult.NotLoggedIn
    return UserHomeTimelineResult.Success(
        posts = timeline.posts.map {
            UserHomeTimelineResult.Post(
                id = it.id,
                username = it.username,
                host = it.host,
                displayName = it.displayName,
                displayNameEmojis = it.displayNameEmojis.map { emoji -> UserCustomEmoji(name = emoji.name, imageUrl = emoji.imageUrl) },
                iconUrl = it.iconUrl,
                body = UserPostBody(
                    content = it.content,
                    publishedAt = it.publishedAt,
                    summary = it.summary,
                    sensitive = it.sensitive,
                    linkUrls = it.linkUrls,
                    images = it.images.map { image -> UserPostImage(url = image.url, thumbnailUrl = image.thumbnailUrl, description = image.description) },
                    favouriteCount = it.favouriteCount,
                    boostCount = it.boostCount,
                    stamps = it.stamps.map { stamp -> UserPostStamp(emoji = stamp.emoji, imageUrl = stamp.imageUrl, count = stamp.count) },
                    emojis = it.emojis.map { emoji -> UserCustomEmoji(name = emoji.name, imageUrl = emoji.imageUrl) },
                    shareUrl = it.url,
                    likedByMe = it.likedByMe,
                    boostedByMe = it.boostedByMe,
                ),
                boostedBy = it.boostedBy?.let { booster ->
                    UserHomeTimelineResult.Booster(
                        username = booster.username,
                        host = booster.host,
                        displayName = booster.displayName,
                        displayNameEmojis = booster.displayNameEmojis.map { emoji ->
                            UserCustomEmoji(name = emoji.name, imageUrl = emoji.imageUrl)
                        },
                        iconUrl = booster.iconUrl,
                    )
                },
                reactionPostId = it.reactionPostId,
                deletableNoteId = it.deletableNoteId,
                followingGroupId = it.followingGroupId,
            )
        },
        nextCursor = timeline.nextCursor,
    )
}

/**
 * 取り直した 1 ページ目を、読み込み済みの投稿の先頭へつなぐ。
 * 1 ページ目の最後の投稿が読み込み済みに無いと、その間の投稿が抜けるので null
 */
fun HomeTimelineQuery.Data.withFirstPage(firstPage: HomeTimelineQuery.Data): HomeTimelineQuery.Data? {
    val loadedTimeline = userInfo?.homeTimeline ?: return null
    val firstPageInfo = firstPage.userInfo ?: return null
    val firstPageTimeline = firstPageInfo.homeTimeline ?: return null
    val lastFirstPagePostId = firstPageTimeline.posts.lastOrNull()?.id ?: return null
    val joinIndex = loadedTimeline.posts.indexOfFirst { it.id == lastFirstPagePostId }.takeIf { it >= 0 } ?: return null
    return firstPage.copy(
        userInfo = firstPageInfo.copy(
            homeTimeline = firstPageTimeline.copy(
                posts = firstPageTimeline.posts + loadedTimeline.posts.drop(joinIndex + 1),
                nextCursor = loadedTimeline.nextCursor,
            ),
        ),
    )
}

/**
 * 購読で届いた投稿を、取得し直さずに先頭へ足す。[posts] は新しい順
 */
fun HomeTimelineQuery.Data.withArrivedPosts(posts: List<UserHomeTimelineResult.Post>): HomeTimelineQuery.Data {
    val info = userInfo ?: return this
    val timeline = info.homeTimeline ?: return this
    val arrived = posts.map {
        HomeTimelineQuery.Post(
            __typename = "HomeTimelinePost",
            id = it.id,
            content = it.body.content,
            publishedAt = it.body.publishedAt,
            username = it.username,
            host = it.host,
            displayName = it.displayName,
            displayNameEmojis = it.displayNameEmojis.map { emoji -> HomeTimelineQuery.DisplayNameEmoji(__typename = "CustomEmoji", name = emoji.name, imageUrl = emoji.imageUrl) },
            summary = it.body.summary,
            sensitive = it.body.sensitive,
            linkUrls = it.body.linkUrls,
            iconUrl = it.iconUrl,
            images = it.body.images.map { image ->
                HomeTimelineQuery.Image(__typename = "PostImage", url = image.url, thumbnailUrl = image.thumbnailUrl, description = image.description)
            },
            emojis = it.body.emojis.map { emoji -> HomeTimelineQuery.Emoji(__typename = "CustomEmoji", name = emoji.name, imageUrl = emoji.imageUrl) },
            deletableNoteId = it.deletableNoteId,
            favouriteCount = it.body.favouriteCount,
            boostCount = it.body.boostCount,
            boostedBy = it.boostedBy?.let { booster ->
                HomeTimelineQuery.BoostedBy(
                    __typename = "PostBooster",
                    username = booster.username,
                    host = booster.host,
                    displayName = booster.displayName,
                    displayNameEmojis = booster.displayNameEmojis.map { emoji ->
                        HomeTimelineQuery.DisplayNameEmoji1(__typename = "CustomEmoji", name = emoji.name, imageUrl = emoji.imageUrl)
                    },
                    iconUrl = booster.iconUrl,
                )
            },
            stamps = it.body.stamps.map { stamp -> HomeTimelineQuery.Stamp(__typename = "PostStamp", emoji = stamp.emoji, imageUrl = stamp.imageUrl, count = stamp.count) },
            url = it.body.shareUrl,
            likedByMe = it.body.likedByMe == true,
            boostedByMe = it.body.boostedByMe,
            reactionPostId = it.reactionPostId,
            followingGroupId = it.followingGroupId,
        )
    }
    return copy(userInfo = info.copy(homeTimeline = timeline.copy(posts = (arrived + timeline.posts).distinctBy { it.id })))
}

/**
 * いいねを変えた結果を、取得し直さずに反映する。
 * 同じ投稿が元の投稿とブーストの両方で並ぶことがあり、いいねは元の投稿に付くので、[reactionPostId] が同じものをまとめて切り替える
 */
fun HomeTimelineQuery.Data.withLiked(reactionPostId: String, liked: Boolean): HomeTimelineQuery.Data {
    val info = userInfo ?: return this
    val timeline = info.homeTimeline ?: return this
    return copy(
        userInfo = info.copy(
            homeTimeline = timeline.copy(
                posts = timeline.posts.map { post ->
                    if (post.reactionPostId != reactionPostId || post.likedByMe == liked) {
                        post
                    } else {
                        // 数は自分のいいねの分だけ増減させる。他の人のいいねは取り直すまで分からない
                        post.copy(
                            likedByMe = liked,
                            favouriteCount = post.favouriteCount?.let { count -> if (liked) count + 1 else (count - 1).coerceAtLeast(0) },
                        )
                    }
                },
            ),
        ),
    )
}

/**
 * ブーストを変えた結果を、取得し直さずに反映する。
 * 同じ投稿が元の投稿とブーストの両方で並ぶことがあり、ブーストは元の投稿に付くので、[reactionPostId] が同じものをまとめて切り替える
 */
fun HomeTimelineQuery.Data.withBoosted(reactionPostId: String, boosted: Boolean): HomeTimelineQuery.Data {
    val info = userInfo ?: return this
    val timeline = info.homeTimeline ?: return this
    return copy(
        userInfo = info.copy(
            homeTimeline = timeline.copy(
                posts = timeline.posts.map { post ->
                    if (post.reactionPostId != reactionPostId || post.boostedByMe == null || post.boostedByMe == boosted) {
                        post
                    } else {
                        post.copy(
                            boostedByMe = boosted,
                            boostCount = post.boostCount?.let { count -> if (boosted) count + 1 else (count - 1).coerceAtLeast(0) },
                        )
                    }
                },
            ),
        ),
    )
}

/**
 * 消した投稿を、取得し直さずに外す
 */
fun HomeTimelineQuery.Data.withoutNote(deletableNoteId: String): HomeTimelineQuery.Data {
    val info = userInfo ?: return this
    val timeline = info.homeTimeline ?: return this
    return copy(userInfo = info.copy(homeTimeline = timeline.copy(posts = timeline.posts.filterNot { it.deletableNoteId == deletableNoteId })))
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
