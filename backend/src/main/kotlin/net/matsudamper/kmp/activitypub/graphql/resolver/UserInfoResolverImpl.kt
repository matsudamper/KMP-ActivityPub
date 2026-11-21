package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.GraphqlErrorBuilder
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlClientException
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlBlockedAccount
import net.matsudamper.kmp.activitypub.graphql.model.QlDeliveryTargetHost
import net.matsudamper.kmp.activitypub.graphql.model.QlDeliveryTargetLimit
import net.matsudamper.kmp.activitypub.graphql.model.QlFailedDelivery
import net.matsudamper.kmp.activitypub.graphql.model.QlFailedDeliveryPage
import net.matsudamper.kmp.activitypub.graphql.model.QlFollowingGroup
import net.matsudamper.kmp.activitypub.graphql.model.QlNotificationPage
import net.matsudamper.kmp.activitypub.graphql.model.QlPostRevision
import net.matsudamper.kmp.activitypub.graphql.model.QlPostRevisionPage
import net.matsudamper.kmp.activitypub.graphql.model.QlTimelinePage
import net.matsudamper.kmp.activitypub.graphql.model.QlUnknownObjectType
import net.matsudamper.kmp.activitypub.graphql.model.QlUnknownObjectTypePage
import net.matsudamper.kmp.activitypub.graphql.model.QlUserInfo
import net.matsudamper.kmp.activitypub.graphql.model.UserInfoResolver
import net.matsudamper.kmp.activitypub.graphql.otelSupplyAsync
import net.matsudamper.kmp.activitypub.repository.FailedDelivery
import net.matsudamper.kmp.activitypub.repository.UnknownObjectType
import net.matsudamper.kmp.activitypub.shared.FailedDeliveryId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId
import net.matsudamper.kmp.activitypub.shared.PostRevisionId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kmp.activitypub.shared.TimelinePostId
import net.matsudamper.kmp.activitypub.timeline.PostRevisionCursor

class UserInfoResolverImpl : UserInfoResolver {
    override fun timeline(
        userInfo: QlUserInfo,
        followingGroupId: FollowingGroupId?,
        after: String?,
        limit: Int,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlTimelinePage?>> =
        otelSupplyAsync {
            val diContainer = GraphQlEngine.diContainer(env)
            val page = diContainer.timelineService.page(userInfo.id, followingGroupId, after, limit)
            val result = QlTimelinePage(
                posts = page.posts.toQlTimelinePosts(diContainer),
                nextCursor = page.nextCursor,
            )
            DataFetcherResult.Builder<QlTimelinePage?>(result).build()
        }

    override fun notifications(userInfo: QlUserInfo, after: String?, limit: Int, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlNotificationPage?>> =
        otelSupplyAsync {
            val diContainer = GraphQlEngine.diContainer(env)
            val page = diContainer.notificationService.page(userInfo.id, after, limit)
            val result = QlNotificationPage(
                notifications = page.notifications.toQlNotificationGroups(diContainer),
                nextCursor = page.nextCursor,
            )
            DataFetcherResult.Builder<QlNotificationPage?>(result).build()
        }

    override fun postRevisions(
        userInfo: QlUserInfo,
        postId: TimelinePostId,
        after: String?,
        limit: Int,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlPostRevisionPage?>> =
        otelSupplyAsync {
            val diContainer = GraphQlEngine.diContainer(env)
            val post = diContainer.timelineService.viewablePost(userInfo.id, postId.value)
            val page = if (post == null) null else diContainer.timelineService.revisionsPage(userInfo.id, postId.value, after, limit)
            val result = if (post == null || page == null) {
                null
            } else {
                QlPostRevisionPage(
                    revisions = page.revisions.map { revision ->
                        val body = createQlPostBody(
                            contentHtml = revision.contentHtml,
                            summary = revision.summary,
                            sensitive = revision.sensitive,
                            images = revision.images,
                            linkUrlsEnabled = false,
                            diContainer = diContainer,
                        )
                        QlPostRevision(
                            id = PostRevisionId(PostRevisionCursor.encode(revision.position)),
                            content = body.content,
                            summary = body.summary,
                            sensitive = body.sensitive,
                            mentions = body.mentions,
                            images = body.images,
                            videos = body.videos,
                            emojis = createQlPostEmojis(noteRef = post.noteRef, contentHtml = revision.contentHtml, pollOptions = listOf(), remoteEmojis = post.emojis, diContainer = diContainer),
                            revisedAt = revision.revisedAt.toString(),
                        )
                    },
                    nextCursor = page.nextCursor,
                )
            }
            DataFetcherResult.Builder<QlPostRevisionPage?>(result).build()
        }

    override fun followingGroups(userInfo: QlUserInfo, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<List<QlFollowingGroup>>> {
        val groups = GraphQlEngine.diContainer(env).followingGroupService.groups(userInfo.id).map { it.toQlFollowingGroup() }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(groups).build())
    }

    override fun editableNoteBody(userInfo: QlUserInfo, noteId: PublicNoteId, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<String?>> =
        otelSupplyAsync {
            val diContainer = GraphQlEngine.diContainer(env)
            val account = diContainer.accountService.account(userInfo.id)
            val notePostService = diContainer.notePostService
            val body = if (account == null || notePostService == null) null else notePostService.editableBody(username = account.username, publicId = noteId)
            DataFetcherResult.Builder<String?>(body).build()
        }

    override fun mainGroupName(userInfo: QlUserInfo, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<String>> {
        val name = GraphQlEngine.diContainer(env).followingGroupService.mainGroupName(userInfo.id)
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(name).build())
    }

    override fun blockedServers(userInfo: QlUserInfo, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<List<String>>> {
        val hosts = GraphQlEngine.diContainer(env).serverBlockService.hosts(userInfo.id)
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(hosts).build())
    }

    override fun blockedAccounts(userInfo: QlUserInfo, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<List<QlBlockedAccount>>> {
        val accounts = GraphQlEngine.diContainer(env).actorBlockService.list(userInfo.id).map { it.toQlBlockedAccount() }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(accounts).build())
    }

    override fun failedDeliveries(
        userInfo: QlUserInfo,
        after: String?,
        limit: Int,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlFailedDeliveryPage>> {
        requireAdmin(userInfo, env)
        return otelSupplyAsync {
            val page = GraphQlEngine.diContainer(env).failedDeliveryService.page(after = after, limit = limit)
            if (page == null) {
                DataFetcherResult.Builder(QlFailedDeliveryPage(deliveries = listOf(), nextCursor = null))
                    .error(GraphqlErrorBuilder.newError(env).message("after が読めない").build())
                    .build()
            } else {
                val result = QlFailedDeliveryPage(
                    deliveries = page.deliveries.map { it.toQlFailedDelivery() },
                    nextCursor = page.nextCursor,
                )
                DataFetcherResult.Builder(result).build()
            }
        }
    }

    override fun failedDelivery(
        userInfo: QlUserInfo,
        id: FailedDeliveryId,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlFailedDelivery?>> {
        requireAdmin(userInfo, env)
        return otelSupplyAsync {
            val delivery = GraphQlEngine.diContainer(env).failedDeliveryService.find(id)?.toQlFailedDelivery()
            DataFetcherResult.Builder<QlFailedDelivery?>(delivery).build()
        }
    }

    override fun unknownObjectTypes(
        userInfo: QlUserInfo,
        after: String?,
        limit: Int,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUnknownObjectTypePage>> {
        requireAdmin(userInfo, env)
        return otelSupplyAsync {
            val page = GraphQlEngine.diContainer(env).unknownObjectTypeService.page(after = after, limit = limit)
            if (page == null) {
                DataFetcherResult.Builder(QlUnknownObjectTypePage(types = listOf(), nextCursor = null))
                    .error(GraphqlErrorBuilder.newError(env).message("after が読めない").build())
                    .build()
            } else {
                val result = QlUnknownObjectTypePage(
                    types = page.types.map { it.toQlUnknownObjectType() },
                    nextCursor = page.nextCursor,
                )
                DataFetcherResult.Builder(result).build()
            }
        }
    }

    override fun deliveryTargetLimit(
        userInfo: QlUserInfo,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlDeliveryTargetLimit>> {
        requireAdmin(userInfo, env)
        return otelSupplyAsync {
            val service = GraphQlEngine.diContainer(env).deliveryTargetLimitService
            val result = QlDeliveryTargetLimit(
                maxPerHost = service.maxPerHost(),
                hosts = service.hostCounts().map { count ->
                    QlDeliveryTargetHost(host = count.host, deliveryTargetCount = count.deliveryInboxCount)
                },
            )
            DataFetcherResult.Builder(result).build()
        }
    }

    private fun requireAdmin(userInfo: QlUserInfo, env: DataFetchingEnvironment) {
        if (userInfo.id !in GraphQlEngine.diContainer(env).adminAccountIds) throw GraphQlClientException("管理者ではない")
    }

    private fun FailedDelivery.toQlFailedDelivery(): QlFailedDelivery = QlFailedDelivery(
        id = FailedDeliveryId(id),
        kind = kind,
        username = username,
        inbox = inbox,
        attempts = attempts,
        enqueuedAt = enqueuedAt.toString(),
        failedAt = failedAt?.toString(),
        lastError = lastError,
    )

    private fun UnknownObjectType.toQlUnknownObjectType(): QlUnknownObjectType = QlUnknownObjectType(
        typeName = typeName,
        sourceUrl = sourceUrl,
        seenCount = seenCount,
        firstSeenAt = firstSeenAt.toString(),
        lastSeenAt = lastSeenAt.toString(),
    )
}
