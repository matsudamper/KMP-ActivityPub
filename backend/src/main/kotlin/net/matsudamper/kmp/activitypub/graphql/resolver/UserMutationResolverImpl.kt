package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.GraphqlExceptions
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

class UserMutationResolverImpl : UserMutationResolver {
    override fun login(
        userMutation: QlUserMutation,
        query: QlUserLoginQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserLoginResult>> {
        val context = GraphQlEngine.graphQlContext(env)
        val accountService = GraphQlEngine.diContainer(env).accountService

        val accountId = accountService.authenticate(username = query.username, password = query.password)
        val account = accountId?.let { accountService.account(it) }

        val result = if (account == null) {
            QlUserLoginResult(
                session = QlUserSession(account = null),
                failure = QlUserLoginFailure.WRONG_USERNAME_OR_PASSWORD,
            )
        } else {
            context.issueUserSession(account.accountId)
            QlUserLoginResult(
                session = QlUserSession(account = account.toUserGraphqlResponse()),
                failure = null,
            )
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

        val result = when (val composed = NoteComposer.compose(body)) {
            is NoteComposer.ComposeResult.Composed -> {
                val queued = diContainer.noteEnqueuer.enqueue(
                    sender = account.urls,
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
