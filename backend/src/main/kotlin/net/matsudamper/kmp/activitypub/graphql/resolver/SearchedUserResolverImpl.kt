package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.model.QlSearchedUser
import net.matsudamper.kmp.activitypub.graphql.model.SearchedUserResolver
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId

class SearchedUserResolverImpl : SearchedUserResolver {
    override fun following(searchedUser: QlSearchedUser, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<Boolean>> =
        loadFollowing(searchedUser.actorUri, env).thenApply { it?.following ?: false }.toDataFetcherResult()

    override fun followingGroupId(searchedUser: QlSearchedUser, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<FollowingGroupId?>> =
        loadFollowing(searchedUser.actorUri, env).thenApply { it?.groupId }.toDataFetcherResult()
}
