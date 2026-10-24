package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorSearchCursor
import net.matsudamper.kmp.activitypub.repository.RemoteActorSearchRecord

/**
 * ユーザーを探す入り口
 *
 * @param remoteUserLookup DOMAIN が無いと相手のサーバーに問い合わせられないので null
 * @param localDomain このサーバーのドメイン。null ならリクエストのホストをこのサーバーのホストとして扱う
 */
class UserSearchService(
    private val accountRepository: AccountRepository,
    private val remoteActorRepository: RemoteActorRepository,
    private val remoteUserLookup: RemoteUserLookup?,
    private val localDomain: String?,
) {
    /**
     * 名前の一部で、このサーバーのユーザーと、フォローなどで受け取った他のサーバーのユーザーをまとめて名前順に探す
     *
     * @param requestHost DOMAIN が無いときに、このサーバーのユーザーのホストとして使う
     */
    fun searchUsers(
        query: String,
        cursor: UserSearchCursor?,
        requestHost: String,
    ): UserSearchResult = searchByName(keyword = query.trim().removePrefix("@"), cursor = cursor, localHost = localDomain ?: requestHost)

    /**
     * `@user@host` か URL から 1 人を引く。このサーバーのユーザーも同じ書き方で引ける
     *
     * @param remoteLookupAllowed 相手のサーバーへ問い合わせてよいか。入力したホストへこちらから GET するので、
     *   誰でも使えるとこのサーバーを踏み台にした大量のリクエストに使われる
     * @param requestHost DOMAIN が無いときに、このサーバーのホストとして使う
     */
    suspend fun resolveUser(
        query: String,
        remoteLookupAllowed: Boolean,
        requestHost: String,
    ): SearchedUser? {
        val localHost = localDomain ?: requestHost
        val trimmedQuery = query.trim()
        if (trimmedQuery.startsWith("https://") || trimmedQuery.startsWith("http://")) {
            return resolveUrl(url = trimmedQuery, remoteLookupAllowed = remoteLookupAllowed, localHost = localHost)
        }

        val acct = trimmedQuery.removePrefix("acct:").removePrefix("@")
        val username = acct.substringBefore('@')
        val host = acct.substringAfter('@', missingDelimiterValue = "")
        if (username.isEmpty()) return null
        if (host.isEmpty() || host.equals(localHost, ignoreCase = true)) return findLocalUser(username = username, localHost = localHost)
        if (!remoteLookupAllowed || remoteUserLookup == null) return null

        val remoteUser = remoteUserLookup.findByAcct(username = username, host = host) ?: return null
        return remoteUser.toSearchedUser(host = host)
    }

    /**
     * このサーバーの URL はアクターの `/users/{name}` と、画面のプロフィールの `/@{name}` を読む
     */
    private suspend fun resolveUrl(
        url: String,
        remoteLookupAllowed: Boolean,
        localHost: String,
    ): SearchedUser? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val authority = if (uri.port == -1) host else "$host:${uri.port}"
        if (authority.equals(localHost, ignoreCase = true)) {
            val username = localUsernamePattern.matchEntire(uri.path.orEmpty())?.groupValues?.get(1) ?: return null
            return findLocalUser(username = username, localHost = localHost)
        }
        if (!remoteLookupAllowed || remoteUserLookup == null) return null

        val remoteUser = remoteUserLookup.findByUrl(url) ?: return null
        return remoteUser.toSearchedUser(host = host)
    }

    private fun findLocalUser(
        username: String,
        localHost: String,
    ): SearchedUser? {
        val account = accountRepository.findByUsername(username) ?: return null
        return SearchedUser(username = account.username, host = localHost.lowercase(), isLocal = true, displayName = account.username)
    }

    private fun RemoteUser.toSearchedUser(host: String): SearchedUser =
        SearchedUser(username = username, host = host.lowercase(), isLocal = false, displayName = displayName ?: username)

    /**
     * このサーバーのユーザーと他のサーバーのユーザーを、それぞれ 1 ページ分より 1 件多く取って名前順に混ぜる。
     * 次のページはそれぞれ最後に返したものの後ろから取るので、片方だけが多くても取りこぼさない
     */
    private fun searchByName(
        keyword: String,
        cursor: UserSearchCursor?,
        localHost: String,
    ): UserSearchResult {
        if (keyword.isEmpty()) return UserSearchResult.EMPTY

        val accounts =
            if (cursor?.localDone == true) {
                listOf()
            } else {
                accountRepository.searchByUsername(keyword = keyword, afterUsername = cursor?.localLastUsername, limit = PAGE_SIZE + 1)
            }
        val remoteActors =
            if (cursor?.remoteDone == true) {
                listOf()
            } else {
                remoteActorRepository.searchByName(keyword = keyword, after = cursor?.remoteLast(), limit = PAGE_SIZE + 1)
            }

        val page =
            mergeKeepingSourceOrder(
                locals = accounts.map { Candidate.Local(it) },
                remotes = remoteActors.map { Candidate.Remote(it) },
                merged = listOf(),
            )
        val pageAccounts = page.filterIsInstance<Candidate.Local>().map { it.account }
        val pageRemoteActors = page.filterIsInstance<Candidate.Remote>().map { it.record }

        val lastAccount = pageAccounts.lastOrNull()
        val lastRemoteActor = pageRemoteActors.lastOrNull()
        val nextCursor =
            UserSearchCursor(
                localDone = cursor?.localDone == true || (accounts.size <= PAGE_SIZE && pageAccounts.size == accounts.size),
                localLastUsername = lastAccount?.username ?: cursor?.localLastUsername,
                remoteDone = cursor?.remoteDone == true || (remoteActors.size <= PAGE_SIZE && pageRemoteActors.size == remoteActors.size),
                remoteLastPreferredUsername = lastRemoteActor?.preferredUsername ?: cursor?.remoteLastPreferredUsername,
                remoteLastActorUri = lastRemoteActor?.actorUri ?: cursor?.remoteLastActorUri,
            )

        return UserSearchResult(
            users = page.mapNotNull { it.toSearchedUser(localHost) },
            nextCursor = nextCursor.takeUnless { it.localDone && it.remoteDone },
        )
    }

    /**
     * 先頭どうしを比べて小さい方から取る。並べ直さないのは、それぞれの取得順の先頭からしか取らないようにするため。
     * 次のページの位置は最後に返したものの後ろなので、途中を飛ばして返すとその間が二度と返らない
     */
    private tailrec fun mergeKeepingSourceOrder(
        locals: List<Candidate.Local>,
        remotes: List<Candidate.Remote>,
        merged: List<Candidate>,
    ): List<Candidate> {
        if (merged.size >= PAGE_SIZE) return merged
        val local = locals.firstOrNull()
        val remote = remotes.firstOrNull()
        return when {
            local == null && remote == null -> merged

            remote == null || (local != null && local.sortName <= remote.sortName) ->
                mergeKeepingSourceOrder(locals = locals.drop(1), remotes = remotes, merged = merged + checkNotNull(local))

            else -> mergeKeepingSourceOrder(locals = locals, remotes = remotes.drop(1), merged = merged + remote)
        }
    }

    private sealed interface Candidate {
        /**
         * 2 つの取得結果を混ぜる順。SQLite の lower() と同じく ASCII だけを小文字にして比べる
         */
        val sortName: String

        fun toSearchedUser(localHost: String): SearchedUser?

        class Local(
            val account: Account,
        ) : Candidate {
            override val sortName: String = account.username.asciiLowercase()

            override fun toSearchedUser(localHost: String): SearchedUser =
                SearchedUser(username = account.username, host = localHost, isLocal = true, displayName = account.username)
        }

        class Remote(
            val record: RemoteActorSearchRecord,
        ) : Candidate {
            override val sortName: String = record.preferredUsername.asciiLowercase()

            /**
             * ホストが読めないアクターは acct を作れないので出さない
             */
            override fun toSearchedUser(localHost: String): SearchedUser? {
                val host = runCatching { URI(record.actorUri).host }.getOrNull() ?: return null
                return SearchedUser(
                    username = record.preferredUsername,
                    host = host.lowercase(),
                    isLocal = false,
                    displayName = record.displayName?.takeIf { it.isNotBlank() } ?: record.preferredUsername,
                )
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 30

        val localUsernamePattern = Regex("^/(?:users/|@)([^/]+)$")
    }
}

data class UserSearchResult(
    val users: List<SearchedUser>,
    val nextCursor: UserSearchCursor?,
) {
    companion object {
        val EMPTY: UserSearchResult = UserSearchResult(users = listOf(), nextCursor = null)
    }
}

/**
 * @param isLocal このサーバーのユーザーか
 * @param displayName 表示名が無ければユーザー名
 */
data class SearchedUser(
    val username: String,
    val host: String,
    val isLocal: Boolean,
    val displayName: String,
)

/**
 * このサーバーのユーザーと他のサーバーのユーザーの、それぞれどこまで返したか。
 * 取り終えたかどうかを別に持つのは、1 件も返していないことと最後まで返したことを区別するため
 */
@Serializable
data class UserSearchCursor(
    @SerialName("localDone")
    val localDone: Boolean,
    @SerialName("localLastUsername")
    val localLastUsername: String?,
    @SerialName("remoteDone")
    val remoteDone: Boolean,
    @SerialName("remoteLastPreferredUsername")
    val remoteLastPreferredUsername: String?,
    @SerialName("remoteLastActorUri")
    val remoteLastActorUri: String?,
) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(Json.encodeToString(serializer(), this).toByteArray())

    fun remoteLast(): RemoteActorSearchCursor? {
        val preferredUsername = remoteLastPreferredUsername ?: return null
        val actorUri = remoteLastActorUri ?: return null
        return RemoteActorSearchCursor(preferredUsername = preferredUsername, actorUri = actorUri)
    }

    companion object {
        /**
         * 読めなければ null
         */
        fun decode(encoded: String): UserSearchCursor? =
            runCatching {
                Json.decodeFromString(serializer(), Base64.getUrlDecoder().decode(encoded).decodeToString())
            }.getOrNull()
    }
}

private fun String.asciiLowercase(): String = map { if (it in 'A'..'Z') it.lowercaseChar() else it }.joinToString("")
