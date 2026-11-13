package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.model.ListedActorResolver
import net.matsudamper.kmp.activitypub.graphql.model.QlListedActor
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId

class ListedActorResolverImpl : ListedActorResolver {
    override fun following(listedActor: QlListedActor, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<Boolean>> =
        loadFollowing(listedActor.actorUri, env).thenApply { it?.following ?: false }.toDataFetcherResult()

    override fun followingGroupId(listedActor: QlListedActor, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<FollowingGroupId?>> =
        loadFollowing(listedActor.actorUri, env).thenApply { it?.groupId }.toDataFetcherResult()
}
