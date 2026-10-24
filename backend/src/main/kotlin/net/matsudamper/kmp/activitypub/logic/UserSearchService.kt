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
 * ユーザー検索の入り口。
 *
 * 名前の一部で、このサーバーのユーザーと、フォローなどで受け取った他のサーバーのユーザーをまとめて名前順に探す。
 * `@user@host` の形なら、そのホストへ WebFinger で問い合わせる
 *
 * @param remoteUserLookup DOMAIN が無いと相手のサーバーに問い合わせられないので null
 * @param localDomain このサーバーのドメイン。`@user@このドメイン` はこのサーバーのユーザーとして探す
 */
class UserSearchService(
    private val accountRepository: AccountRepository,
    private val remoteActorRepository: RemoteActorRepository,
    private val remoteUserLookup: RemoteUserLookup?,
    private val localDomain: String?,
) {
    /**
     * @param remoteLookupAllowed 相手のサーバーへ問い合わせてよいか。入力したホストへこちらから GET するので、
     *   誰でも使えるとこのサーバーを踏み台にした大量のリクエストに使われる
     */
    suspend fun search(
        query: String,
        cursor: UserSearchCursor?,
        remoteLookupAllowed: Boolean,
    ): UserSearchResult {
        val acct = query.trim().removePrefix("@")
        val username = acct.substringBefore('@')
        val host = acct.substringAfter('@', missingDelimiterValue = "")
        if (host.isEmpty() || host.equals(localDomain, ignoreCase = true)) {
            return searchByName(keyword = username, cursor = cursor)
        }
        if (username.isEmpty() || !remoteLookupAllowed || remoteUserLookup == null) return UserSearchResult.EMPTY

        val remoteUser = remoteUserLookup.find(username = username, host = host) ?: return UserSearchResult.EMPTY
        return UserSearchResult(
            users = listOf(
                SearchedUser(
                    username = remoteUser.username,
                    host = host.lowercase(),
                    displayName = remoteUser.displayName ?: remoteUser.username,
                ),
            ),
            nextCursor = null,
        )
    }

    /**
     * このサーバーのユーザーと他のサーバーのユーザーを、それぞれ 1 ページ分より 1 件多く取って名前順に混ぜる。
     * 次のページはそれぞれ最後に返したものの後ろから取るので、片方だけが多くても取りこぼさない
     */
    private fun searchByName(
        keyword: String,
        cursor: UserSearchCursor?,
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
            (accounts.map { Candidate.Local(it) } + remoteActors.map { Candidate.Remote(it) })
                .sortedWith(compareBy<Candidate> { it.sortName }.thenBy { it.sortTieBreaker })
                .take(PAGE_SIZE)
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
            users = page.mapNotNull { it.toSearchedUser() },
            nextCursor = nextCursor.takeUnless { it.localDone && it.remoteDone },
        )
    }

    private sealed interface Candidate {
        val sortName: String

        /**
         * 名前が同じなら、このサーバーのユーザーを先に、他のサーバーのユーザーはアクター URL 順にする。
         * それぞれの取得順と揃えておかないと、次のページの位置とずれる
         */
        val sortTieBreaker: String

        fun toSearchedUser(): SearchedUser?

        class Local(
            val account: Account,
        ) : Candidate {
            override val sortName: String = account.username.lowercase()
            override val sortTieBreaker: String = ""

            override fun toSearchedUser(): SearchedUser =
                SearchedUser(username = account.username, host = null, displayName = account.username)
        }

        class Remote(
            val record: RemoteActorSearchRecord,
        ) : Candidate {
            override val sortName: String = record.preferredUsername.lowercase()
            override val sortTieBreaker: String = record.actorUri

            /**
             * ホストが読めないアクターは acct を作れないので出さない
             */
            override fun toSearchedUser(): SearchedUser? {
                val host = runCatching { URI(record.actorUri).host }.getOrNull() ?: return null
                return SearchedUser(
                    username = record.preferredUsername,
                    host = host.lowercase(),
                    displayName = record.displayName?.takeIf { it.isNotBlank() } ?: record.preferredUsername,
                )
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 30
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
 * @param host このサーバーのユーザーなら null
 * @param displayName 表示名が無ければユーザー名
 */
data class SearchedUser(
    val username: String,
    val host: String?,
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
