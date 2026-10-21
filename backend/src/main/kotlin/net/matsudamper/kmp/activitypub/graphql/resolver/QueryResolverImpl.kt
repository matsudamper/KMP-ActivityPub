package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlClientException
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimeline
import net.matsudamper.kmp.activitypub.graphql.model.QlRegisteredPasskey
import net.matsudamper.kmp.activitypub.graphql.model.QlTimelineNote
import net.matsudamper.kmp.activitypub.graphql.model.QlUser
import net.matsudamper.kmp.activitypub.graphql.model.QlUserInfo
import net.matsudamper.kmp.activitypub.graphql.model.QueryResolver
import net.matsudamper.kmp.activitypub.timeline.TimelineCursor

class QueryResolverImpl : QueryResolver {
    override fun unused(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<Boolean?>> {
        return CompletableFuture.completedFuture(DataFetcherResult.Builder<Boolean?>(null).build())
    }

    override fun userInfo(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlUserInfo?>> {
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId()
        val userInfo =
            if (accountId == null) {
                null
            } else {
                QlUserInfo(id = accountId)
            }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder<QlUserInfo?>(userInfo).build())
    }

    override fun passkeys(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<List<QlRegisteredPasskey>?>> {
        val passkeyService = GraphQlEngine.diContainer(env).passkeyService
        val passkeys =
            GraphQlEngine.graphQlContext(env).userAccountId()?.let { accountId ->
                passkeyService.passkeys(accountId).map { QlRegisteredPasskey(id = it.credentialId, name = it.name) }
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder<List<QlRegisteredPasskey>?>(passkeys).build())
    }

    override fun hasPassword(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<Boolean?>> {
        val accountService = GraphQlEngine.diContainer(env).accountService
        val hasPassword = GraphQlEngine.graphQlContext(env).userAccountId()?.let { accountService.hasPassword(it) }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder<Boolean?>(hasPassword).build())
    }

    override fun user(
        username: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUser?>> {
        val account = GraphQlEngine.diContainer(env).accountService.account(username = username)
        val user =
            if (account == null) {
                null
            } else {
                QlUser(username = account.username)
            }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder<QlUser?>(user).build())
    }

    override fun homeTimeline(
        cursor: String?,
        size: Int,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlHomeTimeline?>> {
        if (size !in 1..MAX_HOME_TIMELINE_SIZE) throw GraphQlClientException("size は 1 から $MAX_HOME_TIMELINE_SIZE まで")
        val after =
            if (cursor == null) {
                null
            } else {
                TimelineCursor.decode(cursor) ?: throw GraphQlClientException("cursor の形が違う")
            }
        val diContainer = GraphQlEngine.diContainer(env)
        // セッションが残っていてもアカウントが消えていることがある
        val account =
            GraphQlEngine.graphQlContext(env).userAccountId()?.let { diContainer.accountService.account(it) }
        val timeline =
            if (account == null) {
                null
            } else {
                val page = diContainer.homeTimelineService.list(account = account, after = after, limit = size)
                QlHomeTimeline(
                    notes = page.notes.map { note ->
                        QlTimelineNote(
                            id = note.publicId.value,
                            username = note.username,
                            contentHtml = note.contentHtml,
                            publishedAt = note.publishedAt.toString(),
                        )
                    },
                    nextCursor = page.next?.let { TimelineCursor.encode(it) },
                )
            }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder<QlHomeTimeline?>(timeline).build())
    }

    private companion object {
        const val MAX_HOME_TIMELINE_SIZE = 40
    }
}
