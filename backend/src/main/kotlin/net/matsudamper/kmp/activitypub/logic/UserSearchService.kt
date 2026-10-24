package net.matsudamper.kmp.activitypub.logic

/**
 * ユーザー検索の入り口。`@user@host` の形ならそのホストのユーザーを、それ以外はこのサーバーのユーザーを探す
 *
 * @param remoteUserLookup DOMAIN が無いと相手のサーバーに問い合わせられないので null
 * @param localDomain このサーバーのドメイン。`@user@このドメイン` はこのサーバーのユーザーとして探す
 */
class UserSearchService(
    private val accountService: AccountService,
    private val remoteUserLookup: RemoteUserLookup?,
    private val localDomain: String?,
) {
    /**
     * @param remoteSearchAllowed 相手のサーバーへ問い合わせてよいか。入力したホストへこちらから GET するので、
     *   誰でも使えるとこのサーバーを踏み台にした大量のリクエストに使われる
     */
    suspend fun search(
        query: String,
        cursor: AccountSearchCursor?,
        remoteSearchAllowed: Boolean,
    ): UserSearchResult {
        val acct = query.trim().removePrefix("@")
        val username = acct.substringBefore('@')
        val host = acct.substringAfter('@', missingDelimiterValue = "")
        if (host.isEmpty() || host.equals(localDomain, ignoreCase = true)) {
            return searchLocal(keyword = username, cursor = cursor)
        }
        if (username.isEmpty()) return UserSearchResult(users = listOf(), nextCursor = null)

        if (!remoteSearchAllowed || remoteUserLookup == null) return UserSearchResult(users = listOf(), nextCursor = null)
        val remoteUser = remoteUserLookup.find(username = username, host = host)
            ?: return UserSearchResult(users = listOf(), nextCursor = null)
        return UserSearchResult(
            users = listOf(
                SearchedUser(username = remoteUser.username, host = host.lowercase(), displayName = remoteUser.displayName),
            ),
            nextCursor = null,
        )
    }

    private fun searchLocal(
        keyword: String,
        cursor: AccountSearchCursor?,
    ): UserSearchResult {
        val result = accountService.searchAccounts(keyword = keyword, cursor = cursor)
        return UserSearchResult(
            users = result.accounts.map { SearchedUser(username = it.username, host = null, displayName = null) },
            nextCursor = result.nextCursor,
        )
    }
}

data class UserSearchResult(
    val users: List<SearchedUser>,
    val nextCursor: AccountSearchCursor?,
)

/**
 * @param host このサーバーのユーザーなら null
 * @param displayName このサーバーのユーザーはまだ表示名を持たないので null
 */
data class SearchedUser(
    val username: String,
    val host: String?,
    val displayName: String?,
)
