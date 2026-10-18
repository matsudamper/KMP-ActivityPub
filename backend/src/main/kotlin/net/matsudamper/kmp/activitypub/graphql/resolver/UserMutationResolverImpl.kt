package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.GraphqlExceptions
import net.matsudamper.kmp.activitypub.graphql.GraphQlClientException
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUserMutation
import net.matsudamper.kmp.activitypub.graphql.model.QlUserNote
import net.matsudamper.kmp.activitypub.graphql.model.QlUserPostNoteFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUserPostNoteResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUserSession
import net.matsudamper.kmp.activitypub.graphql.model.UserMutationResolver
import net.matsudamper.kmp.activitypub.logic.NoteComposer
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kmp.activitypub.telemetry.TraceLogger

class UserMutationResolverImpl : UserMutationResolver {
    /**
     * 通るとセッションの Cookie を返す
     */
    override fun login(
        userMutation: QlUserMutation,
        query: QlUserLoginQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserLoginResult>> {
        val context = GraphQlEngine.graphQlContext(env)
        if (!context.claimLoginAttempt()) throw GraphQlClientException("login は 1 リクエストにつき 1 回まで")
        val diContainer = GraphQlEngine.diContainer(env)
        val loginThrottle = diContainer.loginThrottle
        val clientIp = context.clientIp
        val username = query.username.trim()
        if (!loginThrottle.tryReserve(clientIp = clientIp, username = username)) {
            val traceLogger = TraceLogger.impl()
            traceLogger.setAttribute("login.client_ip", clientIp)
            traceLogger.setAttribute("login.username", username)
            traceLogger.noticeInfo("ログインの失敗が続いている IP なので照合せずに断り、締め出しを延ばした")
            val result = QlUserLoginResult(failure = QlUserLoginFailure.WRONG_USERNAME_OR_PASSWORD)
            return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
        }

        val accountId = diContainer.accountService.authenticate(username = username, password = query.password)
        val result =
            if (accountId == null) {
                QlUserLoginResult(failure = QlUserLoginFailure.WRONG_USERNAME_OR_PASSWORD)
            } else {
                loginThrottle.recordSuccess(clientIp = clientIp, username = username)
                context.issueUserSession(accountId)
                QlUserLoginResult(failure = null)
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    override fun logout(
        userMutation: QlUserMutation,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserSession>> {
        GraphQlEngine.graphQlContext(env).clearUserSession()

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlUserSession(account = null)).build())
    }

    override fun postNote(
        userMutation: QlUserMutation,
        body: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserPostNoteResult>> {
        val diContainer = GraphQlEngine.diContainer(env)
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId() ?: throw GraphqlExceptions.User()
        val account = diContainer.accountService.account(accountId) ?: throw GraphqlExceptions.User()
        val federation = diContainer.federation ?: throw GraphQlClientException("DOMAIN が未設定なので投稿を配れない")
        // 鍵の無いアカウントはアクターとして引けない。署名できないので投稿も受け付けない
        val sender = federation.directory.resolve(account.username)
            ?: throw GraphQlClientException("このアカウントには配信に使う鍵が無い")

        val result = when (val composed = NoteComposer.compose(body)) {
            is NoteComposer.ComposeResult.Composed -> {
                val queued = federation.noteEnqueuer.enqueue(
                    sender = sender,
                    contentHtml = composed.contentHtml,
                )

                QlUserPostNoteResult(
                    note = QlUserNote(
                        id = PublicNoteId(queued.publicId.value),
                        url = queued.url,
                        contentHtml = queued.contentHtml,
                        publishedAt = queued.publishedAt.epochSecond,
                    ),
                    failure = null,
                )
            }

            NoteComposer.ComposeResult.Empty -> QlUserPostNoteResult(
                note = null,
                failure = QlUserPostNoteFailure(isEmpty = true, maxLength = null),
            )

            NoteComposer.ComposeResult.TooLong -> QlUserPostNoteResult(
                note = null,
                failure = QlUserPostNoteFailure(isEmpty = false, maxLength = NoteComposer.MAX_LENGTH),
            )
        }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }
}
