package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.dataloader.ViewerReactionDataLoaderDefine
import net.matsudamper.kmp.activitypub.graphql.model.QlViewerReaction
import net.matsudamper.kmp.activitypub.shared.TimelinePostId

/**
 * @param reactionPostId 付け外しできない投稿は null
 */
internal fun loadViewerReaction(reactionPostId: TimelinePostId?, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlViewerReaction?>> {
    val viewer = GraphQlEngine.graphQlContext(env).userAccountId()
    if (viewer == null || reactionPostId == null) {
        return CompletableFuture.completedFuture<QlViewerReaction?>(null).toDataFetcherResult()
    }
    return GraphQlEngine.dataLoaders(env).viewerReactionDataLoader.get(env)
        .load(ViewerReactionDataLoaderDefine.Key(viewer = viewer, member = reactionPostId.value))
        .thenApply<QlViewerReaction?> { reaction -> reaction?.let { QlViewerReaction(emoji = it.emoji, imageUrl = it.imageUrl) } }
        .toDataFetcherResult()
}
