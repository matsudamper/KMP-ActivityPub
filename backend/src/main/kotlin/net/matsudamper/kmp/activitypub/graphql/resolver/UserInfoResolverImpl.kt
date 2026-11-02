package net.matsudamper.kmp.activitypub.graphql.resolver

import java.net.URI
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimeline
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimelinePost
import net.matsudamper.kmp.activitypub.graphql.model.QlLinkPreview
import net.matsudamper.kmp.activitypub.graphql.model.QlUserInfo
import net.matsudamper.kmp.activitypub.graphql.model.UserInfoResolver
import net.matsudamper.kmp.activitypub.graphql.otelFuture
import net.matsudamper.kmp.activitypub.graphql.otelSupplyAsync
import net.matsudamper.kmp.activitypub.logic.htmlToPlainText

class UserInfoResolverImpl : UserInfoResolver {
    override fun homeTimeline(userInfo: QlUserInfo, cursor: String?, limit: Int, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlHomeTimeline?>> =
        otelSupplyAsync {
            val diContainer = GraphQlEngine.diContainer(env)
            val page = diContainer.homeTimelineService.page(userInfo.id, cursor, limit)
            val result = QlHomeTimeline(
                posts = page.posts.map { post ->
                    QlHomeTimelinePost(
                        id = post.position.member,
                        content = htmlToPlainText(post.contentHtml),
                        publishedAt = post.publishedAt.toString(),
                        username = post.username,
                        host = post.actorUri?.let { URI(it).host },
                        displayName = post.displayName ?: post.username,
                        summary = post.summary,
                        sensitive = post.sensitive,
                        linkUrls = diContainer.linkPreviewService?.links(post.contentHtml).orEmpty(),
                    )
                },
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
