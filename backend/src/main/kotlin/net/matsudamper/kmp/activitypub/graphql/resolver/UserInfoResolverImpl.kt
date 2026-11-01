package net.matsudamper.kmp.activitypub.graphql.resolver

import java.net.URI
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimeline
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimelinePost
import net.matsudamper.kmp.activitypub.graphql.model.QlUserInfo
import net.matsudamper.kmp.activitypub.graphql.model.UserInfoResolver
import net.matsudamper.kmp.activitypub.graphql.otelSupplyAsync
import net.matsudamper.kmp.activitypub.logic.htmlToPlainText

class UserInfoResolverImpl : UserInfoResolver {
    override fun homeTimeline(userInfo: QlUserInfo, cursor: String?, limit: Int, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlHomeTimeline?>> =
        otelSupplyAsync {
            val page = GraphQlEngine.diContainer(env).homeTimelineService.page(userInfo.id, cursor, limit)
            val result = QlHomeTimeline(
                posts = page.posts.map { post ->
                    QlHomeTimelinePost(
                        content = htmlToPlainText(post.contentHtml),
                        publishedAt = post.publishedAt.toString(),
                        username = post.username,
                        host = post.actorUri?.let { URI(it).host },
                        displayName = post.displayName ?: post.username,
                        summary = post.summary,
                        sensitive = post.sensitive,
                    )
                },
                nextCursor = page.nextCursor,
            )
            DataFetcherResult.Builder<QlHomeTimeline?>(result).build()
        }
}
