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
                iconUrl = it.iconUrl,
                body = UserPostBody(
                    content = it.content,
                    publishedAt = it.publishedAt,
                    summary = it.summary,
                    sensitive = it.sensitive,
                    linkUrls = it.linkUrls,
                    images = it.images.map { image -> UserPostImage(url = image.url, description = image.description) },
                ),
            )
        },
        nextCursor = timeline.nextCursor,
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
            summary = it.body.summary,
            sensitive = it.body.sensitive,
            linkUrls = it.body.linkUrls,
            iconUrl = it.iconUrl,
            images = it.body.images.map { image -> HomeTimelineQuery.Image(__typename = "PostImage", url = image.url, description = image.description) },
        )
    }
    return copy(userInfo = info.copy(homeTimeline = timeline.copy(posts = (arrived + timeline.posts).distinctBy { it.id })))
}

fun HomeScreenSearchUsersQuery.Data.toUserSearchResult(): UserSearchResult.Success {
    return UserSearchResult.Success(
        users = searchUsers.users.map {
            UserSearchResult.User(
                actorUri = it.actorUri,
                following = it.following,
                username = it.username,
                host = it.host,
                isLocal = it.isLocal,
                displayName = it.displayName,
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
            users = searchUsers.users.map { if (it.actorUri == change.actorUri) it.copy(following = change.following) else it },
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
    return copy(users = users.map { if (it.actorUri == change.actorUri) it.copy(following = change.following) else it })
}

private fun ActorListFields.toUserFollowListResult(): UserFollowListResult.Success {
    return UserFollowListResult.Success(
        users = users.map {
            UserFollowListResult.User(
                actorUri = it.actorUri,
                following = it.following,
                username = it.username,
                host = it.host,
                displayName = it.displayName,
                iconUrl = it.iconUrl,
            )
        },
        nextCursor = nextCursor,
    )
}
