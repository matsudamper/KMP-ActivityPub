package net.matsudamper.kmp.activitypub.graphql.resolver

import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlinx.coroutines.runBlocking
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlClientException
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlActorList
import net.matsudamper.kmp.activitypub.graphql.model.QlListedActor
import net.matsudamper.kmp.activitypub.graphql.model.QlPostAuthor
import net.matsudamper.kmp.activitypub.graphql.model.QlProfile
import net.matsudamper.kmp.activitypub.graphql.model.QlProfileLink
import net.matsudamper.kmp.activitypub.graphql.model.QlProfilePost
import net.matsudamper.kmp.activitypub.graphql.model.QlProfilePostLike
import net.matsudamper.kmp.activitypub.graphql.model.QlRegisteredPasskey
import net.matsudamper.kmp.activitypub.graphql.model.QlSearchedUser
import net.matsudamper.kmp.activitypub.graphql.model.QlUser
import net.matsudamper.kmp.activitypub.graphql.model.QlUserInfo
import net.matsudamper.kmp.activitypub.graphql.model.QlUserProfile
import net.matsudamper.kmp.activitypub.graphql.model.QlUserSearchResult
import net.matsudamper.kmp.activitypub.graphql.model.QueryResolver
import net.matsudamper.kmp.activitypub.graphql.otelSupplyAsync
import net.matsudamper.kmp.activitypub.logic.ActorListCursor
import net.matsudamper.kmp.activitypub.logic.ActorListPage
import net.matsudamper.kmp.activitypub.logic.SearchedUser
import net.matsudamper.kmp.activitypub.logic.UserSearchCursor
import net.matsudamper.kmp.activitypub.repository.ProfileLink
import net.matsudamper.kmp.activitypub.shared.AccountId

class QueryResolverImpl : QueryResolver {
    override fun userProfile(acct: String, env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlUserProfile?>> {
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId()
        val diContainer = GraphQlEngine.diContainer(env)
        return otelSupplyAsync {
            val profile = runBlocking {
                diContainer.userProfileService.profile(acct, accountId)
            }
            val result =
                if (profile == null) {
                    null
                } else {
                    QlUserProfile(
                        user = profile.user.toQlSearchedUser(env, accountId).copy(following = profile.following),
                        biography = profile.biography,
                        links = profile.links.map { it.toQlProfileLink() },
                        posts = profile.posts.mapNotNull { post ->
                            // 投稿者のホストを出せないブーストは、誰の投稿か示せないので一覧から外す
                            val boostedNoteAuthor = post.boostedNoteAuthor?.let { author ->
                                QlPostAuthor(
                                    username = author.username,
                                    host = URI(author.actorUri).host ?: return@mapNotNull null,
                                    displayName = author.displayName ?: author.username,
                                    iconUrl = author.iconUrl?.let { diContainer.remoteImageProxy?.proxyUrlOf(remoteUrl = it, updatedAt = author.updatedAt) },
                                )
                            }
                            val body = createQlPostBody(
                                contentHtml = post.contentHtml,
                                summary = post.summary,
                                sensitive = post.sensitive,
                                images = post.images,
                                linkUrlsEnabled = post.linkPreviewId != null,
                                diContainer = diContainer,
                            )
                            QlProfilePost(
                                content = body.content,
                                publishedAt = post.publishedAt,
                                summary = body.summary,
                                sensitive = body.sensitive,
                                linkPreviewId = post.linkPreviewId,
                                postId = post.postId,
                                deletableNoteId = post.deletableNoteId,
                                linkUrls = body.linkUrls,
                                images = body.images,
                                favouriteCount = post.favouriteCount?.toInt(),
                                stamps = post.stamps.toQlPostStamps(diContainer),
                                like = post.like?.let { like -> QlProfilePostLike(postId = like.postId, likedByMe = like.likedByMe) },
                                url = diContainer.noteShareUrls?.url(post.noteRef),
                                boostCount = post.boostCount?.toInt(),
                                boostedNoteAuthor = boostedNoteAuthor,
                            )
                        },
                    )
                }
            DataFetcherResult.Builder<QlUserProfile?>(result).build()
        }
    }

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
        val passkeyService = GraphQlEngine.diContainer(env).passkeyService ?: throw GraphQlClientException("パスキーは無効")
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

    override fun profile(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlProfile?>> {
        val diContainer = GraphQlEngine.diContainer(env)
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId()
        val account = accountId?.let { diContainer.accountService.account(it) }
        val profileDetail = account?.let { diContainer.accountService.profileDetail(it.username) }
        val followCounts = accountId?.let { diContainer.followListService.counts(it) }
        val profile =
            if (account == null || profileDetail == null || followCounts == null) {
                null
            } else {
                QlProfile(
                    username = account.username,
                    displayName = account.displayName,
                    iconUrl = diContainer.profileIconUrls?.find(account.username),
                    summary = profileDetail.summary,
                    links = profileDetail.links.map { it.toQlProfileLink() },
                    followingCount = followCounts.following.toInt(),
                    followersCount = followCounts.followers.toInt(),
                )
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder<QlProfile?>(profile).build())
    }

    override fun user(
        username: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUser?>> {
        val diContainer = GraphQlEngine.diContainer(env)
        val account = diContainer.accountService.account(username = username)
        val user =
            if (account == null) {
                null
            } else {
                QlUser(username = account.username, iconUrl = diContainer.profileIconUrls?.find(account.username))
            }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder<QlUser?>(user).build())
    }

    override fun searchUsers(
        query: String,
        after: String?,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserSearchResult>> {
        val cursor = after?.let { UserSearchCursor.decode(it) ?: throw GraphQlClientException("after が読めない") }
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId()
        val result =
            GraphQlEngine.diContainer(env).userSearchService.searchUsers(
                query = query,
                cursor = cursor,
            )
        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(
                QlUserSearchResult(
                    users = result.users.map { it.toQlSearchedUser(env, accountId) },
                    nextCursor = result.nextCursor?.encode(),
                ),
            ).build(),
        )
    }

    override fun resolveUser(
        query: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlSearchedUser?>> {
        val userSearchService = GraphQlEngine.diContainer(env).userSearchService
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId()
        return otelSupplyAsync {
            val user =
                runBlocking {
                    userSearchService.resolveUser(query = query, remoteLookupAllowed = accountId != null)
                }
            DataFetcherResult.Builder<QlSearchedUser?>(user?.toQlSearchedUser(env, accountId)).build()
        }
    }

    override fun followingUsers(
        after: String?,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlActorList?>> {
        val cursor = after?.let { ActorListCursor.decode(it) ?: throw GraphQlClientException("after が読めない") }
        val followListService = GraphQlEngine.diContainer(env).followListService
        val actorList =
            GraphQlEngine.graphQlContext(env).userAccountId()?.let { accountId ->
                followListService.followingUsers(accountId = accountId, cursor = cursor).toQlActorList(env)
            }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder<QlActorList?>(actorList).build())
    }

    override fun followerUsers(
        after: String?,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlActorList?>> {
        val cursor = after?.let { ActorListCursor.decode(it) ?: throw GraphQlClientException("after が読めない") }
        val followListService = GraphQlEngine.diContainer(env).followListService
        val actorList =
            GraphQlEngine.graphQlContext(env).userAccountId()?.let { accountId ->
                followListService.followerUsers(accountId = accountId, cursor = cursor)?.toQlActorList(env)
            }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder<QlActorList?>(actorList).build())
    }

    private fun ActorListPage.toQlActorList(env: DataFetchingEnvironment): QlActorList {
        val diContainer = GraphQlEngine.diContainer(env)
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId()
        return QlActorList(
            users = users.map { user ->
                QlListedActor(
                    actorUri = user.actorUri,
                    following = diContainer.followingService.isFollowing(accountId, user.actorUri),
                    followingGroupId = diContainer.followingGroupService.groupIdOf(accountId, user.actorUri),
                    username = user.username,
                    host = user.host,
                    displayName = user.displayName,
                    iconUrl = user.iconUrl?.let { diContainer.remoteImageProxy?.proxyUrlOf(remoteUrl = it, updatedAt = user.updatedAt) },
                )
            },
            nextCursor = nextCursor?.encode(),
        )
    }

    private fun SearchedUser.toQlSearchedUser(env: DataFetchingEnvironment, accountId: AccountId?): QlSearchedUser {
        val diContainer = GraphQlEngine.diContainer(env)
        return QlSearchedUser(
            actorUri = actorUri,
            following = diContainer.followingService.isFollowing(
                accountId,
                actorUri,
            ),
            followingGroupId = diContainer.followingGroupService.groupIdOf(accountId, actorUri),
            username = username,
            host = host,
            isLocal = isLocal,
            displayName = displayName,
            iconUrl =
            if (isLocal) {
                diContainer.profileIconUrls?.find(username)
            } else {
                iconUrl?.let { diContainer.remoteImageProxy?.proxyUrlOf(remoteUrl = it, updatedAt = updatedAt) }
            },
        )
    }

    private fun ProfileLink.toQlProfileLink(): QlProfileLink = QlProfileLink(name = name, url = url)
}
