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

        val result = service.searchUsers(query = "a", cursor = null)

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

        val result = service.searchUsers(query = "ali", cursor = null)

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

        val firstPage = service.searchUsers(query = "user", cursor = null)
        val firstCursor = assertNotNull(firstPage.nextCursor)
        val secondPage =
            service.searchUsers(query = "user", cursor = UserSearchCursor.decode(firstCursor.encode()))

        assertEquals((1..30).map { "user%02d".format(it) }, firstPage.users.map { it.username })
        assertEquals((31..40).map { "user%02d".format(it) }, secondPage.users.map { it.username })
        assertNull(secondPage.nextCursor)
    }

    @Test
    fun `ASCII以外の大文字を含む名前でもページをまたいで取りこぼさない`() {
        (0..30).forEach { index ->
            listOf("Ä", "ä").forEach { prefix ->
                val name = "$prefix%02d".format(index)
                remoteActorRepository.add(
                    RemoteActorSearchRecord(actorUri = "https://remote.example/users/$name", preferredUsername = name, displayName = "common"),
                )
            }
        }

        val names = generateSequence(service.searchUsers(query = "common", cursor = null)) { page ->
            page.nextCursor?.let { cursor ->
                service.searchUsers(query = "common", cursor = cursor)
            }
        }.flatMap { it.users }.map { it.username }.toList()

        assertEquals(62, names.size)
        assertEquals(62, names.toSet().size)
    }

    @Test
    fun `空白だけの検索語では何も返さない`() {
        addAccount("user1")

        assertEquals(UserSearchResult.EMPTY, service.searchUsers(query = " ", cursor = null))
    }

    @Test
    fun `このサーバーのドメインのacctはこのサーバーのユーザーを引き他のサーバーには問い合わせない`() {
        addAccount("alice")

        val user = runBlocking { service.resolveUser(query = "@alice@LOCAL.example", remoteLookupAllowed = true) }

        assertEquals(SearchedUser(username = "alice", host = "local.example", isLocal = true, displayName = "alice"), user)
        assertEquals(listOf(), remoteUserLookup.requests)
    }

    @Test
    fun `このサーバーのアクターとプロフィールのURLでこのサーバーのユーザーを引く`() {
        addAccount("alice")
        val expected = SearchedUser(username = "alice", host = "local.example", isLocal = true, displayName = "alice")

        assertEquals(expected, runBlocking { service.resolveUser(query = "https://local.example/users/alice", remoteLookupAllowed = false) })
        assertEquals(expected, runBlocking { service.resolveUser(query = "https://local.example/@alice", remoteLookupAllowed = false) })
        assertNull(runBlocking { service.resolveUser(query = "https://local.example/notes/1", remoteLookupAllowed = false) })
    }

    @Test
    fun `他のホストのacctはそのホストのユーザーを引く`() {
        remoteUserLookup.users["bob@Remote.example"] = RemoteUser(username = "bob", displayName = null)

        val user = runBlocking { service.resolveUser(query = " @bob@Remote.example ", remoteLookupAllowed = true) }

        assertEquals(SearchedUser(username = "bob", host = "remote.example", isLocal = false, displayName = "bob"), user)
    }

    @Test
    fun `他のホストのURLはそのURLのユーザーを引く`() {
        remoteUserLookup.users["https://remote.example/@bob"] = RemoteUser(username = "bob", displayName = "Bob")

        val user = runBlocking { service.resolveUser(query = "https://remote.example/@bob", remoteLookupAllowed = true) }

        assertEquals(SearchedUser(username = "bob", host = "remote.example", isLocal = false, displayName = "Bob"), user)
    }

    @Test
    fun `他のホストで見つからなければnullを返す`() {
        assertNull(runBlocking { service.resolveUser(query = "@bob@remote.example", remoteLookupAllowed = true) })
    }

    @Test
    fun `他のホストへの問い合わせが許されていなければ問い合わせない`() {
        remoteUserLookup.users["bob@remote.example"] = RemoteUser(username = "bob", displayName = "Bob")
        remoteUserLookup.users["https://remote.example/@bob"] = RemoteUser(username = "bob", displayName = "Bob")

        assertNull(runBlocking { service.resolveUser(query = "@bob@remote.example", remoteLookupAllowed = false) })
        assertNull(runBlocking { service.resolveUser(query = "https://remote.example/@bob", remoteLookupAllowed = false) })
        assertEquals(listOf(), remoteUserLookup.requests)
    }

    @Test
    fun `ユーザー名の無いacctでは問い合わせない`() {
        assertNull(runBlocking { service.resolveUser(query = "@@remote.example", remoteLookupAllowed = true) })
        assertEquals(listOf(), remoteUserLookup.requests)
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

        override suspend fun findByAcct(
            username: String,
            host: String,
        ): RemoteUser? {
            requests += "$username@$host"
            return users["$username@$host"]
        }

        override suspend fun findByUrl(url: String): RemoteUser? {
            requests += url
            return users[url]
        }
    }
}
