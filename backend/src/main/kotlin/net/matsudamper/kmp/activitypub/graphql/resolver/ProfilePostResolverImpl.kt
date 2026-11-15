package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.model.ProfilePostResolver
import net.matsudamper.kmp.activitypub.graphql.model.QlProfilePost
import net.matsudamper.kmp.activitypub.graphql.model.QlViewerReaction

class ProfilePostResolverImpl : ProfilePostResolver {
    override fun viewerReaction(profilePost: QlProfilePost, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlViewerReaction?>> =
        loadViewerReaction(profilePost.reactionPostId, env)
}
