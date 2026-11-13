package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.dataloader.FollowingDataLoaderDefine

/**
 * ログイン中のアカウントから見た [actorUri] のフォロー状態。ログインしていないか、このサーバーのアカウントなら null
 */
internal fun loadFollowing(actorUri: String?, env: DataFetchingEnvironment): CompletableFuture<FollowingDataLoaderDefine.Following?> {
    val viewer = GraphQlEngine.graphQlContext(env).viewerAccountId
    if (viewer == null || actorUri == null) return CompletableFuture.completedFuture(null)
    return GraphQlEngine.dataLoaders(env).followingDataLoader.get(env)
        .load(FollowingDataLoaderDefine.Key(viewer = viewer, actorUri = actorUri))
        .thenApply { it }
}

internal fun <T> CompletableFuture<T>.toDataFetcherResult(): CompletionStage<DataFetcherResult<T>> =
    thenApply { DataFetcherResult.Builder<T>(it).build() }
