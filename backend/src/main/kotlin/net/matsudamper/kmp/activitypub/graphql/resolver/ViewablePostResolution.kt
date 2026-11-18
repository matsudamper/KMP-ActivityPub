package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.GraphqlErrorBuilder
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlTimelinePost
import net.matsudamper.kmp.activitypub.graphql.otelFuture
import net.matsudamper.kmp.activitypub.logic.ReplyTargetResult
import net.matsudamper.kmp.activitypub.logic.ReplyTargetService
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 返信先や引用先の投稿を引く
 *
 * @param viewer ログインしていなければ null
 */
internal fun linkedPost(
    viewer: AccountId?,
    env: DataFetchingEnvironment,
    unavailableMessage: String,
    lookup: suspend ReplyTargetService.() -> ReplyTargetResult,
): CompletionStage<DataFetcherResult<QlTimelinePost?>> {
    val diContainer = GraphQlEngine.diContainer(env)
    return otelFuture {
        when (val result = diContainer.replyTargetService?.lookup()) {
            is ReplyTargetResult.Found -> {
                val mapped = listOf(result.post).toQlTimelinePosts(diContainer).single()
                val post = if (diContainer.viewablePostService.isOwnedByViewer(viewer, result.post)) mapped else mapped.copy(deletableNoteId = null, pinnableNoteId = null)
                DataFetcherResult.Builder<QlTimelinePost?>(post).build()
            }

            ReplyTargetResult.NotFound,
            null,
            -> DataFetcherResult.Builder<QlTimelinePost?>(null).build()

            // 返信先や引用先が無いのと区別して、画面が取り直せるようにする
            ReplyTargetResult.Unavailable ->
                DataFetcherResult.Builder<QlTimelinePost?>(null)
                    .error(GraphqlErrorBuilder.newError(env).message(unavailableMessage).build())
                    .build()
        }
    }
}
