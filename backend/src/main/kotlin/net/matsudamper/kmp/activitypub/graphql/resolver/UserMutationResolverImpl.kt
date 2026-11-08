package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.io.encoding.Base64
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlClientException
import net.matsudamper.kmp.activitypub.graphql.GraphQlContext
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.GraphQlUpload
import net.matsudamper.kmp.activitypub.graphql.model.QlCreateFollowingGroupFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlCreateFollowingGroupResult
import net.matsudamper.kmp.activitypub.graphql.model.QlDeleteNoteFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlDeleteNoteResult
import net.matsudamper.kmp.activitypub.graphql.model.QlDeletePasskeyFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlDeletePasskeyResult
import net.matsudamper.kmp.activitypub.graphql.model.QlDeletePasswordFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlDeletePasswordResult
import net.matsudamper.kmp.activitypub.graphql.model.QlRegisterPasskeyFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlRegisterPasskeyQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlRegisterPasskeyResult
import net.matsudamper.kmp.activitypub.graphql.model.QlSetLikedFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlSetLikedResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUpdateProfileFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUpdateProfileIconFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUpdateProfileIconResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUpdateProfileQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlUpdateProfileResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUploadNoteImageFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUploadNoteImageResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUserMutation
import net.matsudamper.kmp.activitypub.graphql.model.QlUserPasskeyLoginFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUserPasskeyLoginResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUserPostNoteFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUserPostNoteResult
import net.matsudamper.kmp.activitypub.graphql.model.UserMutationResolver
import net.matsudamper.kmp.activitypub.graphql.otelFuture
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.CreateFollowingGroupResult
import net.matsudamper.kmp.activitypub.logic.DeleteNoteResult
import net.matsudamper.kmp.activitypub.logic.NoteImageUpload
import net.matsudamper.kmp.activitypub.logic.PasskeyService
import net.matsudamper.kmp.activitypub.logic.PostNoteResult
import net.matsudamper.kmp.activitypub.logic.SetLikedResult
import net.matsudamper.kmp.activitypub.logic.UpdateIconResult
import net.matsudamper.kmp.activitypub.logic.UpdateProfileResult
import net.matsudamper.kmp.activitypub.logic.UploadNoteImageResult
import net.matsudamper.kmp.activitypub.repository.FollowingGroupId
import net.matsudamper.kmp.activitypub.repository.PasskeyDeleteResult
import net.matsudamper.kmp.activitypub.repository.ProfileLink
import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId
import net.matsudamper.kmp.activitypub.shared.PasskeyCredentialId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kmp.activitypub.telemetry.TraceLogger

class UserMutationResolverImpl : UserMutationResolver {
    override fun setFollowing(
        userMutation: QlUserMutation,
        actorUri: String,
        followed: Boolean,
        groupId: String?,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<Boolean>> {
        val followingGroupId =
            if (groupId == null) {
                null
            } else {
                val parsed = groupId.toLongOrNull()
                    ?: return CompletableFuture.completedFuture(DataFetcherResult.Builder(false).build())
                FollowingGroupId(parsed)
            }
        return otelFuture {
            val result = GraphQlEngine.diContainer(env).followingGroupService.setFollowing(
                accountId = GraphQlEngine.graphQlContext(env).userAccountId(),
                actorUri = actorUri,
                followed = followed,
                groupId = followingGroupId,
            )
            DataFetcherResult.Builder(result).build()
        }
    }

    override fun createFollowingGroup(
        userMutation: QlUserMutation,
        name: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlCreateFollowingGroupResult>> {
        val result = GraphQlEngine.diContainer(env).followingGroupService.createGroup(
            accountId = GraphQlEngine.graphQlContext(env).userAccountId(),
            name = name,
        )
        val qlResult = when (result) {
            is CreateFollowingGroupResult.Created -> QlCreateFollowingGroupResult(group = result.group.toQlFollowingGroup(), failure = null)
            CreateFollowingGroupResult.NotLoggedIn -> QlCreateFollowingGroupResult(group = null, failure = QlCreateFollowingGroupFailure.NOT_LOGGED_IN)
            CreateFollowingGroupResult.InvalidName -> QlCreateFollowingGroupResult(group = null, failure = QlCreateFollowingGroupFailure.INVALID_NAME)
        }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(qlResult).build())
    }

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

    override fun passkeyLoginOptions(
        userMutation: QlUserMutation,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<String>> {
        val context = GraphQlEngine.graphQlContext(env)
        claimPasskeyChallenge(context)

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(passkeyService(env).loginOptions()).build())
    }

    override fun passkeyLogin(
        userMutation: QlUserMutation,
        credential: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserPasskeyLoginResult>> {
        val context = GraphQlEngine.graphQlContext(env)
        if (!context.claimLoginAttempt()) throw GraphQlClientException("login は 1 リクエストにつき 1 回まで")

        val accountId = passkeyService(env).login(credentialJson = credential)
        val result =
            if (accountId == null) {
                QlUserPasskeyLoginResult(failure = QlUserPasskeyLoginFailure.NOT_ACCEPTED)
            } else {
                context.issueUserSession(accountId)
                QlUserPasskeyLoginResult(failure = null)
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    override fun passkeyRegistrationOptions(
        userMutation: QlUserMutation,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<String?>> {
        val context = GraphQlEngine.graphQlContext(env)
        val account =
            context.userAccountId()?.let { GraphQlEngine.diContainer(env).accountService.account(it) }
                ?: return CompletableFuture.completedFuture(DataFetcherResult.Builder<String?>(null).build())
        claimPasskeyChallenge(context)

        return CompletableFuture.completedFuture(DataFetcherResult.Builder<String?>(passkeyService(env).registrationOptions(account)).build())
    }

    override fun registerPasskey(
        userMutation: QlUserMutation,
        query: QlRegisterPasskeyQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlRegisterPasskeyResult>> {
        val context = GraphQlEngine.graphQlContext(env)
        val passkeyService = passkeyService(env)

        val accountId = context.userAccountId()
        val failure =
            if (accountId == null) {
                QlRegisterPasskeyFailure.NOT_LOGGED_IN
            } else {
                when (passkeyService.register(accountId = accountId, name = query.name, credentialJson = query.credential)) {
                    PasskeyService.RegistrationResult.Success -> null
                    PasskeyService.RegistrationResult.NotAccepted -> QlRegisterPasskeyFailure.NOT_ACCEPTED
                    PasskeyService.RegistrationResult.AlreadyRegistered -> QlRegisterPasskeyFailure.ALREADY_REGISTERED
                    PasskeyService.RegistrationResult.InvalidName -> QlRegisterPasskeyFailure.INVALID_NAME
                }
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlRegisterPasskeyResult(failure = failure)).build())
    }

    override fun deletePasskey(
        userMutation: QlUserMutation,
        id: PasskeyCredentialId,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlDeletePasskeyResult>> {
        val context = GraphQlEngine.graphQlContext(env)
        val passkeyService = passkeyService(env)

        val accountId = context.userAccountId()
        val failure =
            if (accountId == null) {
                QlDeletePasskeyFailure.NOT_LOGGED_IN
            } else {
                when (passkeyService.delete(accountId = accountId, credentialId = id)) {
                    PasskeyDeleteResult.Deleted -> null
                    PasskeyDeleteResult.NotFound -> QlDeletePasskeyFailure.NOT_FOUND
                    PasskeyDeleteResult.LastLoginMethod -> QlDeletePasskeyFailure.LAST_LOGIN_METHOD
                }
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlDeletePasskeyResult(failure = failure)).build())
    }

    override fun deletePassword(
        userMutation: QlUserMutation,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlDeletePasswordResult>> {
        if (GraphQlEngine.diContainer(env).passkeyService == null) throw GraphQlClientException("パスキーが無効なのでパスワードを消せない")
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId()
        val failure =
            if (accountId == null) {
                QlDeletePasswordFailure.NOT_LOGGED_IN
            } else {
                when (GraphQlEngine.diContainer(env).accountService.deletePassword(accountId)) {
                    AccountService.DeletePasswordResult.Success -> null
                    AccountService.DeletePasswordResult.NoPasskey -> QlDeletePasswordFailure.NO_PASSKEY
                }
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlDeletePasswordResult(failure = failure)).build())
    }

    override fun logout(
        userMutation: QlUserMutation,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<Boolean>> {
        GraphQlEngine.graphQlContext(env).revokeUserSession()
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(true).build())
    }

    override fun postNote(
        userMutation: QlUserMutation,
        body: String,
        requestId: String,
        imageIds: List<NoteImagePublicId>,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserPostNoteResult>> {
        val result = postNote(body = body, requestId = requestId, imageIds = imageIds, env = env)
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    override fun uploadNoteImage(
        userMutation: QlUserMutation,
        image: GraphQlUpload,
        description: String?,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUploadNoteImageResult>> {
        val result = uploadNoteImage(image = image, description = description, env = env)
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    override fun deleteNote(
        userMutation: QlUserMutation,
        noteId: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlDeleteNoteResult>> {
        val result = QlDeleteNoteResult(failure = deleteNote(noteId = noteId, env = env))
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    override fun updateProfile(
        userMutation: QlUserMutation,
        query: QlUpdateProfileQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUpdateProfileResult>> {
        val result = QlUpdateProfileResult(failure = updateProfile(query = query, env = env))
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    override fun updateProfileIcon(
        userMutation: QlUserMutation,
        icon: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUpdateProfileIconResult>> {
        val result = updateProfileIcon(icon = icon, env = env)
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    override fun setLiked(
        userMutation: QlUserMutation,
        postId: String,
        liked: Boolean,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlSetLikedResult>> {
        val result = setLiked(postId = postId, liked = liked, env = env)
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    private fun passkeyService(env: DataFetchingEnvironment): PasskeyService =
        GraphQlEngine.diContainer(env).passkeyService ?: throw GraphQlClientException("パスキーは無効")

    private fun claimPasskeyChallenge(context: GraphQlContext) {
        if (!context.claimPasskeyChallenge()) throw GraphQlClientException("パスキーの challenge は 1 リクエストにつき 1 回まで")
    }

    private fun postNote(
        body: String,
        requestId: String,
        imageIds: List<NoteImagePublicId>,
        env: DataFetchingEnvironment,
    ): QlUserPostNoteResult {
        val diContainer = GraphQlEngine.diContainer(env)
        val accountId =
            GraphQlEngine.graphQlContext(env).userAccountId()
                ?: return QlUserPostNoteResult(url = null, failure = QlUserPostNoteFailure.NOT_LOGGED_IN)
        // セッションが残っていてもアカウントが消えていることがある
        val account =
            diContainer.accountService.account(accountId)
                ?: return QlUserPostNoteResult(url = null, failure = QlUserPostNoteFailure.NOT_LOGGED_IN)
        val notePostService =
            diContainer.notePostService
                ?: return QlUserPostNoteResult(url = null, failure = QlUserPostNoteFailure.FEDERATION_DISABLED)
        val result =
            notePostService.post(
                username = account.username,
                body = body,
                requestId = requestId,
                imageIds = imageIds,
            )
        val failure =
            when (result) {
                is PostNoteResult.Posted -> return QlUserPostNoteResult(url = result.url, failure = null)
                PostNoteResult.EmptyBody -> QlUserPostNoteFailure.EMPTY_BODY
                PostNoteResult.TooLong -> QlUserPostNoteFailure.TOO_LONG
                PostNoteResult.TooManyImages -> QlUserPostNoteFailure.TOO_MANY_IMAGES
                PostNoteResult.ImageNotFound -> QlUserPostNoteFailure.IMAGE_NOT_FOUND
                PostNoteResult.AccountNotFound -> QlUserPostNoteFailure.NOT_LOGGED_IN
            }
        return QlUserPostNoteResult(url = null, failure = failure)
    }

    private fun deleteNote(
        noteId: String,
        env: DataFetchingEnvironment,
    ): QlDeleteNoteFailure? {
        val diContainer = GraphQlEngine.diContainer(env)
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId() ?: return QlDeleteNoteFailure.NOT_LOGGED_IN
        // セッションが残っていてもアカウントが消えていることがある
        val account = diContainer.accountService.account(accountId) ?: return QlDeleteNoteFailure.NOT_LOGGED_IN
        val notePostService = diContainer.notePostService ?: return QlDeleteNoteFailure.FEDERATION_DISABLED
        return when (notePostService.delete(username = account.username, publicId = PublicNoteId(noteId))) {
            DeleteNoteResult.Deleted -> null
            DeleteNoteResult.NotFound -> QlDeleteNoteFailure.NOT_FOUND
            DeleteNoteResult.ImageStorageDisabled -> QlDeleteNoteFailure.INTERNAL_SERVER_ERROR
            DeleteNoteResult.ImageDeleteFailed -> QlDeleteNoteFailure.INTERNAL_SERVER_ERROR
            DeleteNoteResult.AccountNotFound -> QlDeleteNoteFailure.NOT_LOGGED_IN
        }
    }

    private fun uploadNoteImage(
        image: GraphQlUpload,
        description: String?,
        env: DataFetchingEnvironment,
    ): QlUploadNoteImageResult {
        val diContainer = GraphQlEngine.diContainer(env)
        val accountId =
            GraphQlEngine.graphQlContext(env).userAccountId()
                ?: return QlUploadNoteImageResult(imageId = null, failure = QlUploadNoteImageFailure.NOT_LOGGED_IN)
        // セッションが残っていてもアカウントが消えていることがある
        val account =
            diContainer.accountService.account(accountId)
                ?: return QlUploadNoteImageResult(imageId = null, failure = QlUploadNoteImageFailure.NOT_LOGGED_IN)
        val notePostService =
            diContainer.notePostService
                ?: return QlUploadNoteImageResult(imageId = null, failure = QlUploadNoteImageFailure.UNSUPPORTED_OPERATION)
        val failure =
            when (val result = notePostService.uploadImage(username = account.username, image = NoteImageUpload(size = image.size, openStream = image::openStream, description = description))) {
                is UploadNoteImageResult.Uploaded -> return QlUploadNoteImageResult(imageId = result.publicId, failure = null)
                UploadNoteImageResult.TooLarge -> QlUploadNoteImageFailure.TOO_LARGE
                UploadNoteImageResult.UnsupportedImage -> QlUploadNoteImageFailure.UNSUPPORTED_IMAGE
                UploadNoteImageResult.DescriptionTooLong -> QlUploadNoteImageFailure.DESCRIPTION_TOO_LONG
                UploadNoteImageResult.ImageStorageDisabled -> QlUploadNoteImageFailure.UNSUPPORTED_OPERATION
                UploadNoteImageResult.UploadFailed -> QlUploadNoteImageFailure.INTERNAL_SERVER_ERROR
                UploadNoteImageResult.AccountNotFound -> QlUploadNoteImageFailure.NOT_LOGGED_IN
            }
        return QlUploadNoteImageResult(imageId = null, failure = failure)
    }

    private fun updateProfile(
        query: QlUpdateProfileQuery,
        env: DataFetchingEnvironment,
    ): QlUpdateProfileFailure? {
        val diContainer = GraphQlEngine.diContainer(env)
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId() ?: return QlUpdateProfileFailure.NOT_LOGGED_IN
        // セッションが残っていてもアカウントが消えていることがある
        val account = diContainer.accountService.account(accountId) ?: return QlUpdateProfileFailure.NOT_LOGGED_IN
        val profileUpdateService = diContainer.profileUpdateService ?: return QlUpdateProfileFailure.FEDERATION_DISABLED
        if (query.displayName == null && query.summary == null && query.links == null) return null

        val result =
            profileUpdateService.updateProfile(
                username = account.username,
                displayName = query.displayName,
                summary = query.summary,
                links = query.links?.map { ProfileLink(name = it.name, url = it.url) },
            )
        return when (result) {
            UpdateProfileResult.Updated -> null
            UpdateProfileResult.DisplayNameTooLong -> QlUpdateProfileFailure.TOO_LONG
            UpdateProfileResult.SummaryTooLong -> QlUpdateProfileFailure.SUMMARY_TOO_LONG
            UpdateProfileResult.TooManyLinks -> QlUpdateProfileFailure.TOO_MANY_LINKS
            UpdateProfileResult.LinkTooLong -> QlUpdateProfileFailure.LINK_TOO_LONG
            UpdateProfileResult.InvalidLink -> QlUpdateProfileFailure.INVALID_LINK
            UpdateProfileResult.AccountNotFound -> QlUpdateProfileFailure.NOT_LOGGED_IN
        }
    }

    private fun setLiked(
        postId: String,
        liked: Boolean,
        env: DataFetchingEnvironment,
    ): QlSetLikedResult {
        val diContainer = GraphQlEngine.diContainer(env)
        val likeService =
            diContainer.likeService
                ?: return QlSetLikedResult(failure = QlSetLikedFailure.FEDERATION_DISABLED)
        val failure =
            when (likeService.setLiked(accountId = GraphQlEngine.graphQlContext(env).userAccountId(), postId = postId, liked = liked)) {
                SetLikedResult.Updated -> null
                SetLikedResult.NotLoggedIn -> QlSetLikedFailure.NOT_LOGGED_IN
                SetLikedResult.PostNotFound -> QlSetLikedFailure.POST_NOT_FOUND
            }
        return QlSetLikedResult(failure = failure)
    }

    private fun updateProfileIcon(
        icon: String,
        env: DataFetchingEnvironment,
    ): QlUpdateProfileIconResult {
        val diContainer = GraphQlEngine.diContainer(env)
        val accountId =
            GraphQlEngine.graphQlContext(env).userAccountId()
                ?: return QlUpdateProfileIconResult(iconUrl = null, failure = QlUpdateProfileIconFailure.NOT_LOGGED_IN)
        // セッションが残っていてもアカウントが消えていることがある
        val account =
            diContainer.accountService.account(accountId)
                ?: return QlUpdateProfileIconResult(iconUrl = null, failure = QlUpdateProfileIconFailure.NOT_LOGGED_IN)
        val profileUpdateService =
            diContainer.profileUpdateService
                ?: return QlUpdateProfileIconResult(iconUrl = null, failure = QlUpdateProfileIconFailure.FEDERATION_DISABLED)
        val iconBytes =
            runCatching { Base64.Default.decode(icon) }.getOrNull()
                ?: return QlUpdateProfileIconResult(iconUrl = null, failure = QlUpdateProfileIconFailure.UNSUPPORTED_IMAGE)

        val failure =
            when (profileUpdateService.updateIcon(username = account.username, icon = iconBytes)) {
                UpdateIconResult.Updated -> null
                UpdateIconResult.TooLarge -> QlUpdateProfileIconFailure.TOO_LARGE
                UpdateIconResult.DimensionsTooLarge -> QlUpdateProfileIconFailure.DIMENSIONS_TOO_LARGE
                UpdateIconResult.Unsupported -> QlUpdateProfileIconFailure.UNSUPPORTED_IMAGE
                UpdateIconResult.ImageStorageDisabled -> QlUpdateProfileIconFailure.UNSUPPORTED_OPERATION
                UpdateIconResult.UploadFailed -> QlUpdateProfileIconFailure.INTERNAL_SERVER_ERROR
                UpdateIconResult.AccountNotFound -> QlUpdateProfileIconFailure.NOT_LOGGED_IN
            }
        val iconUrl = if (failure == null) diContainer.profileIconUrls?.find(account.username) else null
        return QlUpdateProfileIconResult(iconUrl = iconUrl, failure = failure)
    }
}
