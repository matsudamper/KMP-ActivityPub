package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimeline
import net.matsudamper.kmp.activitypub.graphql.model.QlUserInfo
import net.matsudamper.kmp.activitypub.graphql.model.UserInfoResolver
import net.matsudamper.kmp.activitypub.graphql.otelSupplyAsync

class UserInfoResolverImpl : UserInfoResolver {
    override fun homeTimeline(userInfo: QlUserInfo, cursor: String?, limit: Int, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlHomeTimeline?>> =
        otelSupplyAsync {
            val diContainer = GraphQlEngine.diContainer(env)
            val page = diContainer.homeTimelineService.page(userInfo.id, cursor, limit)
            val result = QlHomeTimeline(
                posts = page.posts.map { it.toQlHomeTimelinePost(diContainer.noteImageUrls) },
                nextCursor = page.nextCursor,
            )
            DataFetcherResult.Builder<QlHomeTimeline?>(result).build()
        }
}
