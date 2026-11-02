package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimeline
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
                posts = page.posts.map { it.toQlHomeTimelinePost(diContainer.linkPreviewService) },
                nextCursor = page.nextCursor,
            )
            DataFetcherResult.Builder<QlHomeTimeline?>(result).build()
        }

    override fun linkPreviews(userInfo: QlUserInfo, postId: String, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<List<QlLinkPreview>>> {
        val diContainer = GraphQlEngine.diContainer(env)
        return otelFuture {
            val linkPreviewService = diContainer.linkPreviewService
            val post = diContainer.homeTimelineService.post(userInfo.id, postId)
            val previews =
                if (linkPreviewService == null || post == null) {
                    listOf()
                } else {
                    linkPreviewService.previews(post.contentHtml).map { preview ->
                        QlLinkPreview(
                            url = preview.url,
                            title = preview.title,
                            siteName = preview.siteName,
                            imageUrl = preview.imageUrl,
                        )
                    }
                }
            DataFetcherResult.Builder<List<QlLinkPreview>>(previews).build()
        }
    }
}
