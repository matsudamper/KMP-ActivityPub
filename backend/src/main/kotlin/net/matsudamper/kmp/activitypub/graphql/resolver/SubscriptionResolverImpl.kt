package net.matsudamper.kmp.activitypub.graphql.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.reactive.asPublisher
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlClientException
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimelinePost
import net.matsudamper.kmp.activitypub.graphql.model.QlRemoteProfileRefreshProgress
import net.matsudamper.kmp.activitypub.graphql.model.QlRemoteProfileRefreshState
import net.matsudamper.kmp.activitypub.graphql.model.SubscriptionResolver
import net.matsudamper.kmp.activitypub.logic.RemoteProfileRefreshProgress
import org.reactivestreams.Publisher

class SubscriptionResolverImpl : SubscriptionResolver {
    override fun homeTimelinePosts(env: DataFetchingEnvironment): Publisher<QlHomeTimelinePost> {
        val context = GraphQlEngine.graphQlContext(env)
        val accountId = context.userAccountId()
            ?: throw GraphQlClientException("ログインしていない")
        val diContainer = GraphQlEngine.diContainer(env)
        return diContainer.homeTimelineArrivals
            .posts(accountId)
            // 接続はログアウトや期限切れの後も残るので、流すたびにセッションを確かめて終える
            .takeWhile { context.userAccountId() == accountId }
            .map { it.toQlHomeTimelinePost(accountId, diContainer) }
            .asPublisher(Dispatchers.IO)
    }

    override fun remoteProfileRefresh(start: Boolean, env: DataFetchingEnvironment): Publisher<QlRemoteProfileRefreshState> {
        val context = GraphQlEngine.graphQlContext(env)
        val accountId = context.userAccountId()
            ?: throw GraphQlClientException("ログインしていない")
        val diContainer = GraphQlEngine.diContainer(env)
        if (accountId !in diContainer.adminAccountIds) throw GraphQlClientException("管理者ではない")
        val service = diContainer.remoteProfileRefreshService
            ?: throw GraphQlClientException("DOMAIN が無いので他のサーバーに取りに行けない")
        // 既に走っていれば始め直さず、その進み具合を流す
        if (start) service.start()
        return service.progress
            .takeWhile { context.userAccountId() == accountId }
            .map { progress -> QlRemoteProfileRefreshState(progress = progress?.toQlRemoteProfileRefreshProgress()) }
            .asPublisher(Dispatchers.IO)
    }

    private fun RemoteProfileRefreshProgress.toQlRemoteProfileRefreshProgress(): QlRemoteProfileRefreshProgress =
        QlRemoteProfileRefreshProgress(
            running = running,
            total = total.toInt(),
            refreshed = refreshed.toInt(),
            failed = failed.toInt(),
            skipped = skipped.toInt(),
            startedAt = startedAt.toString(),
            finishedAt = finishedAt?.toString(),
        )
}
