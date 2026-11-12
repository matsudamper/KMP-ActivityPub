package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlFollowingGroup
import net.matsudamper.kmp.activitypub.graphql.model.QlLinkPreview
import net.matsudamper.kmp.activitypub.graphql.model.QlNotificationPage
import net.matsudamper.kmp.activitypub.graphql.model.QlTimelinePage
import net.matsudamper.kmp.activitypub.graphql.model.QlTimelinePost
import net.matsudamper.kmp.activitypub.graphql.model.QlUserInfo
import net.matsudamper.kmp.activitypub.graphql.model.UserInfoResolver
import net.matsudamper.kmp.activitypub.graphql.otelFuture
import net.matsudamper.kmp.activitypub.graphql.otelSupplyAsync
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId
import net.matsudamper.kmp.activitypub.shared.TimelinePostId

class UserInfoResolverImpl : UserInfoResolver {
    override fun timeline(
        userInfo: QlUserInfo,
        followingGroupId: FollowingGroupId?,
        after: String?,
        limit: Int,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlTimelinePage?>> =
        otelSupplyAsync {
            val diContainer = GraphQlEngine.diContainer(env)
            val page = diContainer.timelineService.page(userInfo.id, followingGroupId, after, limit)
            val result = QlTimelinePage(
                posts = page.posts.toQlTimelinePosts(userInfo.id, diContainer),
                nextCursor = page.nextCursor,
            )
            DataFetcherResult.Builder<QlTimelinePage?>(result).build()
        }

    override fun notifications(userInfo: QlUserInfo, after: String?, limit: Int, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlNotificationPage?>> =
        otelSupplyAsync {
            val diContainer = GraphQlEngine.diContainer(env)
            val page = diContainer.notificationService.page(userInfo.id, after, limit)
            val posts = page.notifications.map { it.post }.distinctBy { it.position.member }.toQlTimelinePosts(userInfo.id, diContainer).associateBy { it.id.value }
            val result = QlNotificationPage(
                notifications = page.notifications.map { it.notification.toQlNotification(posts.getValue(it.post.position.member), diContainer) },
                nextCursor = page.nextCursor,
            )
            DataFetcherResult.Builder<QlNotificationPage?>(result).build()
        }

    override fun timelinePost(userInfo: QlUserInfo, id: TimelinePostId, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlTimelinePost?>> =
        otelSupplyAsync {
            val diContainer = GraphQlEngine.diContainer(env)
            val post = diContainer.timelineService.post(userInfo.id, id.value)?.let { listOf(it).toQlTimelinePosts(userInfo.id, diContainer).single() }
            DataFetcherResult.Builder<QlTimelinePost?>(post).build()
        }

    override fun followingGroups(userInfo: QlUserInfo, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<List<QlFollowingGroup>>> {
        val groups = GraphQlEngine.diContainer(env).followingGroupService.groups(userInfo.id).map { it.toQlFollowingGroup() }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(groups).build())
    }

    override fun mainGroupName(userInfo: QlUserInfo, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<String>> {
        val name = GraphQlEngine.diContainer(env).followingGroupService.mainGroupName(userInfo.id)
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(name).build())
    }

    override fun linkPreviews(userInfo: QlUserInfo, postId: String, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<List<QlLinkPreview>>> {
        val diContainer = GraphQlEngine.diContainer(env)
        return otelFuture {
            val linkPreviewService = diContainer.linkPreviewService
            val contentHtml = diContainer.userProfileService.localNoteContentHtml(postId)
                ?: diContainer.timelineService.post(userInfo.id, postId)?.contentHtml
            val previews =
                if (linkPreviewService == null || contentHtml == null) {
                    listOf()
                } else {
                    linkPreviewService.previews(contentHtml).map { preview ->
                        QlLinkPreview(
                            url = preview.url,
                            title = preview.title,
                            siteName = preview.siteName,
                            imageUrl = preview.imageUrl,
                            faviconUrl = preview.faviconUrl,
                        )
                    }
                }
            DataFetcherResult.Builder<List<QlLinkPreview>>(previews).build()
        }
    }
}
