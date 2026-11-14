package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.GraphqlErrorBuilder
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
import net.matsudamper.kmp.activitypub.logic.ReplyTargetResult
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
                posts = page.posts.toQlTimelinePosts(diContainer),
                nextCursor = page.nextCursor,
            )
            DataFetcherResult.Builder<QlTimelinePage?>(result).build()
        }

    override fun notifications(userInfo: QlUserInfo, after: String?, limit: Int, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlNotificationPage?>> =
        otelSupplyAsync {
            val diContainer = GraphQlEngine.diContainer(env)
            val page = diContainer.notificationService.page(userInfo.id, after, limit)
            val posts = page.notifications.map { it.post }.distinctBy { it.position.member }.toQlTimelinePosts(diContainer).associateBy { it.id.value }
            val result = QlNotificationPage(
                notifications = page.notifications.map { it.notification.toQlNotification(posts.getValue(it.post.position.member), diContainer) },
                nextCursor = page.nextCursor,
            )
            DataFetcherResult.Builder<QlNotificationPage?>(result).build()
        }

    override fun timelinePost(userInfo: QlUserInfo, id: TimelinePostId, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlTimelinePost?>> =
        otelFuture {
            val diContainer = GraphQlEngine.diContainer(env)
            val post = diContainer.viewablePostService.post(userInfo.id, id.value)?.let { viewable ->
                val mapped = listOf(viewable.post).toQlTimelinePosts(diContainer).single()
                if (viewable.ownedByViewer) mapped else mapped.copy(deletableNoteId = null, pinnableNoteId = null)
            }
            DataFetcherResult.Builder<QlTimelinePost?>(post).build()
        }

    override fun replyTarget(userInfo: QlUserInfo, postId: TimelinePostId, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlTimelinePost?>> {
        val diContainer = GraphQlEngine.diContainer(env)
        return otelFuture {
            when (val result = diContainer.replyTargetService?.replyTarget(userInfo.id, postId.value)) {
                is ReplyTargetResult.Found -> {
                    val mapped = listOf(result.post).toQlTimelinePosts(diContainer).single()
                    val post = if (diContainer.viewablePostService.isOwnedByViewer(userInfo.id, result.post)) mapped else mapped.copy(deletableNoteId = null, pinnableNoteId = null)
                    DataFetcherResult.Builder<QlTimelinePost?>(post).build()
                }

                ReplyTargetResult.NotFound,
                null,
                -> DataFetcherResult.Builder<QlTimelinePost?>(null).build()

                // 返信先が無いのと区別して、画面が取り直せるようにする
                ReplyTargetResult.Unavailable ->
                    DataFetcherResult.Builder<QlTimelinePost?>(null)
                        .error(GraphqlErrorBuilder.newError(env).message("返信先のサーバーから投稿を取れなかった").build())
                        .build()
            }
        }
    }

    override fun followingGroups(userInfo: QlUserInfo, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<List<QlFollowingGroup>>> {
        val groups = GraphQlEngine.diContainer(env).followingGroupService.groups(userInfo.id).map { it.toQlFollowingGroup() }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(groups).build())
    }

    override fun mainGroupName(userInfo: QlUserInfo, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<String>> {
        val name = GraphQlEngine.diContainer(env).followingGroupService.mainGroupName(userInfo.id)
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(name).build())
    }

    override fun blockedServers(userInfo: QlUserInfo, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<List<String>>> {
        val hosts = GraphQlEngine.diContainer(env).serverBlockService.hosts(userInfo.id)
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(hosts).build())
    }

    override fun linkPreviews(userInfo: QlUserInfo, postId: TimelinePostId, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<List<QlLinkPreview>>> {
        val diContainer = GraphQlEngine.diContainer(env)
        return otelFuture {
            val linkPreviewService = diContainer.linkPreviewService
            val contentHtml = diContainer.viewablePostService.contentHtml(userInfo.id, postId.value)
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
