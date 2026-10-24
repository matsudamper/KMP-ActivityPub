package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.FakeAccountRepository
import net.matsudamper.kmp.activitypub.FakeRemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorSearchRecord

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class UserSearchServiceTest {
    private val accountRepository = FakeAccountRepository(hasPasskey = { false })
    private val remoteActorRepository = FakeRemoteActorRepository()
    private val remoteUserLookup = FakeRemoteUserLookup()

    private val service =
        UserSearchService(
            accountRepository = accountRepository,
            remoteActorRepository = remoteActorRepository,
            remoteUserLookup = remoteUserLookup,
            localDomain = "local.example",
        )

    @Test
    fun `名前の一部でこのサーバーと他のサーバーのユーザーをまとめて名前順に探す`() {
        addAccount("carol")
        addAccount("alice")
        remoteActorRepository.add(RemoteActorSearchRecord(actorUri = "https://remote.example/users/bob", preferredUsername = "bob", displayName = "Bob"))
        remoteActorRepository.add(RemoteActorSearchRecord(actorUri = "https://remote.example/users/dave", preferredUsername = "dave", displayName = null))

        val result = runBlocking { service.search(query = "a", cursor = null, remoteLookupAllowed = false, requestHost = REQUEST_HOST) }

        assertEquals(
            listOf(
                SearchedUser(username = "alice", host = "local.example", isLocal = true, displayName = "alice"),
                SearchedUser(username = "carol", host = "local.example", isLocal = true, displayName = "carol"),
                SearchedUser(username = "dave", host = "remote.example", isLocal = false, displayName = "dave"),
            ),
            result.users,
        )
        assertNull(result.nextCursor)
        assertEquals(listOf(), remoteUserLookup.requests)
    }

    @Test
    fun `他のサーバーのユーザーは表示名でも探せる`() {
        remoteActorRepository.add(RemoteActorSearchRecord(actorUri = "https://remote.example/users/1", preferredUsername = "user1", displayName = "Alice"))

        val result = runBlocking { service.search(query = "ali", cursor = null, remoteLookupAllowed = false, requestHost = REQUEST_HOST) }

        assertEquals(listOf(SearchedUser(username = "user1", host = "remote.example", isLocal = false, displayName = "Alice")), result.users)
    }

    @Test
    fun `1ページに収まらなければ続きを片方に偏っても取りこぼさずに返す`() {
        (1..20).forEach { addAccount("user%02d".format(it)) }
        (21..40).forEach {
            remoteActorRepository.add(
                RemoteActorSearchRecord(actorUri = "https://remote.example/users/$it", preferredUsername = "user%02d".format(it), displayName = null),
            )
        }

        val firstPage = runBlocking { service.search(query = "user", cursor = null, remoteLookupAllowed = false, requestHost = REQUEST_HOST) }
        val firstCursor = assertNotNull(firstPage.nextCursor)
        val secondPage =
            runBlocking { service.search(query = "user", cursor = UserSearchCursor.decode(firstCursor.encode()), remoteLookupAllowed = false, requestHost = REQUEST_HOST) }

        assertEquals((1..30).map { "user%02d".format(it) }, firstPage.users.map { it.username })
        assertEquals((31..40).map { "user%02d".format(it) }, secondPage.users.map { it.username })
        assertNull(secondPage.nextCursor)
    }

    @Test
    fun `空白だけの検索語では何も返さない`() {
        addAccount("user1")

        assertEquals(UserSearchResult.EMPTY, runBlocking { service.search(query = " ", cursor = null, remoteLookupAllowed = true, requestHost = REQUEST_HOST) })
    }

    @Test
    fun `このサーバーのドメインのacctはこのサーバーのユーザーを探す`() {
        addAccount("alice")

        val result = runBlocking { service.search(query = "@alice@LOCAL.example", cursor = null, remoteLookupAllowed = true, requestHost = REQUEST_HOST) }

        assertEquals(listOf(SearchedUser(username = "alice", host = "local.example", isLocal = true, displayName = "alice")), result.users)
        assertEquals(listOf(), remoteUserLookup.requests)
    }

    @Test
    fun `他のホストのacctはそのホストのユーザーを引く`() {
        remoteUserLookup.users["bob@Remote.example"] = RemoteUser(username = "bob", displayName = null)

        val result = runBlocking { service.search(query = " @bob@Remote.example ", cursor = null, remoteLookupAllowed = true, requestHost = REQUEST_HOST) }

        assertEquals(
            UserSearchResult(users = listOf(SearchedUser(username = "bob", host = "remote.example", isLocal = false, displayName = "bob")), nextCursor = null),
            result,
        )
    }

    @Test
    fun `他のホストで見つからなければ空の結果を返す`() {
        assertEquals(UserSearchResult.EMPTY, runBlocking { service.search(query = "@bob@remote.example", cursor = null, remoteLookupAllowed = true, requestHost = REQUEST_HOST) })
    }

    @Test
    fun `他のホストへの問い合わせが許されていなければ問い合わせない`() {
        remoteUserLookup.users["bob@remote.example"] = RemoteUser(username = "bob", displayName = "Bob")

        val result = runBlocking { service.search(query = "@bob@remote.example", cursor = null, remoteLookupAllowed = false, requestHost = REQUEST_HOST) }

        assertEquals(UserSearchResult.EMPTY, result)
        assertEquals(listOf(), remoteUserLookup.requests)
    }

    @Test
    fun `ユーザー名の無いacctでは問い合わせない`() {
        assertEquals(UserSearchResult.EMPTY, runBlocking { service.search(query = "@@remote.example", cursor = null, remoteLookupAllowed = true, requestHost = REQUEST_HOST) })
        assertEquals(listOf(), remoteUserLookup.requests)
    }

    @Test
    fun `DOMAINが無ければこのサーバーのユーザーのホストにリクエストのホストを使う`() {
        addAccount("alice")
        val serviceWithoutDomain =
            UserSearchService(
                accountRepository = accountRepository,
                remoteActorRepository = remoteActorRepository,
                remoteUserLookup = null,
                localDomain = null,
            )

        val result = runBlocking { serviceWithoutDomain.search(query = "alice", cursor = null, remoteLookupAllowed = true, requestHost = REQUEST_HOST) }

        assertEquals(listOf(SearchedUser(username = "alice", host = REQUEST_HOST, isLocal = true, displayName = "alice")), result.users)
    }

    @Test
    fun `読めないカーソルはnullになる`() {
        assertNull(UserSearchCursor.decode("not-a-cursor"))
    }

    private fun addAccount(username: String) {
        accountRepository.add(username = username, passwordHash = "hash", passwordHashType = "type")
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

    private companion object {
        const val REQUEST_HOST = "request.example"
    }
}
