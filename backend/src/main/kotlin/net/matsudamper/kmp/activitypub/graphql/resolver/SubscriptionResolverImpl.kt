package net.matsudamper.kmp.activitypub.graphql.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.reactive.asPublisher
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.DiContainer
import net.matsudamper.kmp.activitypub.graphql.GraphQlClientException
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlAccountEvent
import net.matsudamper.kmp.activitypub.graphql.model.QlRemoteProfileRefreshProgress
import net.matsudamper.kmp.activitypub.graphql.model.QlRemoteProfileRefreshState
import net.matsudamper.kmp.activitypub.graphql.model.QlTimelinePostsDeleted
import net.matsudamper.kmp.activitypub.graphql.model.SubscriptionResolver
import net.matsudamper.kmp.activitypub.logic.RemoteProfileRefreshProgress
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.TimelinePostId
import net.matsudamper.kmp.activitypub.timeline.AccountEvent
import org.reactivestreams.Publisher

class SubscriptionResolverImpl : SubscriptionResolver {
    override fun accountEvents(env: DataFetchingEnvironment): Publisher<QlAccountEvent> {
        val context = GraphQlEngine.graphQlContext(env)
        val accountId = context.userAccountId()
            ?: throw GraphQlClientException("ログインしていない")
        val diContainer = GraphQlEngine.diContainer(env)
        return diContainer.accountEvents
            .events(accountId)
            // 接続はログアウトや期限切れの後も残るので、流すたびにセッションを確かめて終える
            .takeWhile { context.userAccountId() == accountId }
            .filterNot { event -> diContainer.blockLists.hides(accountId, event) }
            .mapNotNull { event -> event.toQlAccountEvent(accountId, diContainer) }
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

    /**
     * @return 流さなくてよければ null
     */
    private fun AccountEvent.toQlAccountEvent(accountId: AccountId, diContainer: DiContainer): QlAccountEvent? = when (this) {
        is AccountEvent.PostArrived -> QlAccountEvent(
            timelinePostArrived = listOf(post).toQlTimelinePosts(diContainer).single(),
            timelinePostsDeleted = null,
            notificationGroupUpdated = null,
            timelinePostUpdated = null,
        )

        is AccountEvent.PostsDeleted -> QlAccountEvent(
            timelinePostArrived = null,
            timelinePostsDeleted = QlTimelinePostsDeleted(
                postIds = deletedMembers.map { TimelinePostId(it) },
                boostUndonePostIds = boostUndoneMembers.map { TimelinePostId(it) },
            ),
            notificationGroupUpdated = null,
            timelinePostUpdated = null,
        )

        is AccountEvent.NotificationGroupUpdated -> diContainer.notificationService.find(accountId, groupId)?.let { view ->
            QlAccountEvent(
                timelinePostArrived = null,
                timelinePostsDeleted = null,
                notificationGroupUpdated = listOf(view).toQlNotificationGroups(diContainer).single(),
                timelinePostUpdated = null,
            )
        }

        is AccountEvent.PostUpdated -> QlAccountEvent(
            timelinePostArrived = null,
            timelinePostsDeleted = null,
            notificationGroupUpdated = null,
            timelinePostUpdated = listOf(post).toQlTimelinePosts(diContainer).single(),
        )
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
