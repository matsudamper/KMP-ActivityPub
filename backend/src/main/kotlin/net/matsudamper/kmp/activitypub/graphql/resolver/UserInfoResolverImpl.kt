package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlFollowingGroup
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimeline
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimelinePost
import net.matsudamper.kmp.activitypub.graphql.model.QlLinkPreview
import net.matsudamper.kmp.activitypub.graphql.model.QlUserInfo
import net.matsudamper.kmp.activitypub.graphql.model.UserInfoResolver
import net.matsudamper.kmp.activitypub.graphql.otelFuture
import net.matsudamper.kmp.activitypub.graphql.otelSupplyAsync

class UserInfoResolverImpl : UserInfoResolver {
    override fun homeTimeline(userInfo: QlUserInfo, cursor: String?, limit: Int, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlHomeTimeline?>> =
        otelSupplyAsync {
            val diContainer = GraphQlEngine.diContainer(env)
            val page = diContainer.homeTimelineService.page(userInfo.id, cursor, limit)
            val result = QlHomeTimeline(
                posts = page.posts.toQlHomeTimelinePosts(userInfo.id, diContainer),
                nextCursor = page.nextCursor,
            )
            DataFetcherResult.Builder<QlHomeTimeline?>(result).build()
        }

    override fun post(userInfo: QlUserInfo, id: String, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlHomeTimelinePost?>> =
        otelSupplyAsync {
            val diContainer = GraphQlEngine.diContainer(env)
            val post = diContainer.homeTimelineService.post(userInfo.id, id)?.let { listOf(it).toQlHomeTimelinePosts(userInfo.id, diContainer).single() }
            DataFetcherResult.Builder<QlHomeTimelinePost?>(post).build()
        }

    override fun followingGroups(userInfo: QlUserInfo, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<List<QlFollowingGroup>>> {
        val groups = GraphQlEngine.diContainer(env).followingGroupService.groups(userInfo.id).map { it.toQlFollowingGroup() }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(groups).build())
    }

    override fun homeGroupName(userInfo: QlUserInfo, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<String>> {
        val name = GraphQlEngine.diContainer(env).followingGroupService.homeName(userInfo.id)
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(name).build())
    }

    override fun linkPreviews(userInfo: QlUserInfo, postId: String, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<List<QlLinkPreview>>> {
        val diContainer = GraphQlEngine.diContainer(env)
        return otelFuture {
            val linkPreviewService = diContainer.linkPreviewService
            val contentHtml = diContainer.userProfileService.localNoteContentHtml(postId)
                ?: diContainer.homeTimelineService.post(userInfo.id, postId)?.contentHtml
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
