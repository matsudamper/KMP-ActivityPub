package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlTimelinePost
import net.matsudamper.kmp.activitypub.graphql.model.TimelinePostResolver
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId

class TimelinePostResolverImpl : TimelinePostResolver {
    override fun pinned(timelinePost: QlTimelinePost, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<Boolean>> {
        val pinnableNoteId = timelinePost.pinnableNoteId ?: return CompletableFuture.completedFuture(false).toDataFetcherResult()
        return GraphQlEngine.dataLoaders(env).pinnedNoteDataLoader.get(env).load(pinnableNoteId).toDataFetcherResult()
    }

    /**
     * ブーストで載った投稿は、ブーストした相手の購読先に載る
     */
    override fun followingGroupId(timelinePost: QlTimelinePost, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<FollowingGroupId?>> {
        val carrier = timelinePost.boostedBy ?: timelinePost.author
        return loadFollowing(carrier.actorUri, env).thenApply { it?.groupId }.toDataFetcherResult()
    }
}
