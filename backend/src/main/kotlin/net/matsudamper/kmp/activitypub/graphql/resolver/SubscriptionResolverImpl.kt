package net.matsudamper.kmp.activitypub.graphql.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.reactive.asPublisher
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlClientException
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimelinePost
import net.matsudamper.kmp.activitypub.graphql.model.SubscriptionResolver
import org.reactivestreams.Publisher

class SubscriptionResolverImpl : SubscriptionResolver {
    override fun homeTimelinePosts(env: DataFetchingEnvironment): Publisher<QlHomeTimelinePost> {
        val context = GraphQlEngine.graphQlContext(env)
        val accountId = context.userAccountId()
            ?: throw GraphQlClientException("ログインしていない")
        return GraphQlEngine.diContainer(env).homeTimelineArrivals
            .posts(accountId)
            // 接続はログアウトや期限切れの後も残るので、流すたびにセッションを確かめて終える
            .takeWhile { context.userAccountId() == accountId }
            .map { it.toQlHomeTimelinePost() }
            .asPublisher(Dispatchers.IO)
    }
}
