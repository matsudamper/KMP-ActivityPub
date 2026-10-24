package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.FakeAccountRepository

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class UserSearchServiceTest {
    private val accountRepository = FakeAccountRepository(hasPasskey = { false })
    private val remoteUserLookup = FakeRemoteUserLookup()

    private val service =
        UserSearchService(
            accountService = AccountService(accountRepository = accountRepository),
            remoteUserLookup = remoteUserLookup,
            localDomain = "local.example",
        )

    @Test
    fun `ホストの無い検索語はこのサーバーのユーザーを探す`() {
        accountRepository.add(username = "alice", passwordHash = "hash", passwordHashType = "type")

        val result = runBlocking { service.search(query = "@ali", cursor = null, remoteSearchAllowed = true) }

        assertEquals(listOf(SearchedUser(username = "alice", host = null, displayName = null)), result.users)
        assertEquals(listOf(), remoteUserLookup.requests)
    }

    @Test
    fun `このサーバーのドメインのacctはこのサーバーのユーザーを探す`() {
        accountRepository.add(username = "alice", passwordHash = "hash", passwordHashType = "type")

        val result = runBlocking { service.search(query = "@alice@LOCAL.example", cursor = null, remoteSearchAllowed = true) }

        assertEquals(listOf(SearchedUser(username = "alice", host = null, displayName = null)), result.users)
        assertEquals(listOf(), remoteUserLookup.requests)
    }

    @Test
    fun `他のホストのacctはそのホストのユーザーを引く`() {
        remoteUserLookup.users["bob@Remote.example"] = RemoteUser(username = "bob", displayName = "Bob")

        val result = runBlocking { service.search(query = " @bob@Remote.example ", cursor = null, remoteSearchAllowed = true) }

        assertEquals(
            UserSearchResult(users = listOf(SearchedUser(username = "bob", host = "remote.example", displayName = "Bob")), nextCursor = null),
            result,
        )
    }

    @Test
    fun `他のホストで見つからなければ空の結果を返す`() {
        val result = runBlocking { service.search(query = "@bob@remote.example", cursor = null, remoteSearchAllowed = true) }

        assertEquals(UserSearchResult(users = listOf(), nextCursor = null), result)
    }

    @Test
    fun `他のホストへの問い合わせが許されていなければ問い合わせない`() {
        remoteUserLookup.users["bob@remote.example"] = RemoteUser(username = "bob", displayName = "Bob")

        val result = runBlocking { service.search(query = "@bob@remote.example", cursor = null, remoteSearchAllowed = false) }

        assertEquals(UserSearchResult(users = listOf(), nextCursor = null), result)
        assertEquals(listOf(), remoteUserLookup.requests)
    }

    @Test
    fun `ユーザー名の無いacctでは問い合わせない`() {
        val result = runBlocking { service.search(query = "@@remote.example", cursor = null, remoteSearchAllowed = true) }

        assertEquals(UserSearchResult(users = listOf(), nextCursor = null), result)
        assertEquals(listOf(), remoteUserLookup.requests)
    }

    private class FakeRemoteUserLookup : RemoteUserLookup {
        val users: MutableMap<String, RemoteUser> = mutableMapOf()
        val requests: MutableList<String> = mutableListOf()

        override suspend fun find(
            username: String,
            host: String,
        ): RemoteUser? {
            requests += "$username@$host"
            return users["$username@$host"]
        }
    }
}
