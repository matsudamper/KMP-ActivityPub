package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.matsudamper.kmp.activitypub.FakeAccountBoostRepository
import net.matsudamper.kmp.activitypub.FakeAccountRepository
import net.matsudamper.kmp.activitypub.FakeFollowerRepository
import net.matsudamper.kmp.activitypub.repository.BoostTarget
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.activity.ActivityStreamsIri
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.StoredActorNames
import net.matsudamper.kotpub.json.AppJson

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class BoostServiceTest {
    private val accountRepository = FakeAccountRepository(hasPasskey = { false })
    private val alice = accountRepository.add(username = "alice", passwordHash = "hash", passwordHashType = "type")
    private val followerRepository = FakeFollowerRepository().apply { deliveryInboxes += FOLLOWER_INBOX }
    private val boosts = FakeAccountBoostRepository()
    private var enqueuedCount = 0
    private val service = BoostService(
        directory = ActorDirectory(domain = DOMAIN, stored = LocalAccounts),
        accountService = AccountService(accountRepository),
        boosts = boosts,
        unsavedNotes = NoUnsavedRemoteNotes,
        followerRepository = followerRepository,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
        onEnqueued = { enqueuedCount++ },
    )

    @Test
    fun `保存していない投稿はブーストするときに保存し、外すときは保存しない`() = runBlocking<Unit> {
        val imported = mutableListOf<String>()
        val unsavedNotes = object : UnsavedRemoteNotes {
            override fun storedMember(viewer: AccountId, objectUri: String): String? = null

            override suspend fun import(viewer: AccountId, objectUri: String): String {
                imported += objectUri
                return "r1"
            }

            override suspend fun fetch(objectUri: String): RemoteNoteFetchResult = RemoteNoteFetchResult.NotFound
        }
        val importingService = BoostService(
            directory = ActorDirectory(domain = DOMAIN, stored = LocalAccounts),
            accountService = AccountService(accountRepository),
            boosts = boosts,
            unsavedNotes = unsavedNotes,
            followerRepository = followerRepository,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
            onEnqueued = { enqueuedCount++ },
        )
        boosts.targets = mapOf("r1" to BoostTarget.Remote(OBJECT_URI, AUTHOR, AUTHOR_INBOX, unlisted = false))

        assertEquals(SetBoostedResult.Updated, importingService.setBoosted(alice, UnsavedRemoteNoteId.of(OBJECT_URI), boosted = false))
        assertEquals(listOf(), imported)

        assertEquals(SetBoostedResult.Updated, importingService.setBoosted(alice, UnsavedRemoteNoteId.of(OBJECT_URI), boosted = true))
        assertEquals(listOf(OBJECT_URI), imported)
        assertEquals(1, enqueuedCount)
    }

    @Test
    fun `公開の投稿は公開でブーストし、投稿者とフォロワーに送る`() = runBlocking<Unit> {
        boosts.targets = mapOf("r1" to BoostTarget.Remote(OBJECT_URI, AUTHOR, AUTHOR_INBOX, unlisted = false))

        assertEquals(SetBoostedResult.Updated, service.setBoosted(alice, "r1", boosted = true))

        val boost = boosts.boosts.single()
        assertEquals(listOf(FOLLOWER_INBOX, AUTHOR_INBOX), boost.inboxes)
        val announce = AppJson.parseToJsonElement(boost.announceBody).jsonObject
        assertEquals(listOf(ActivityStreamsIri.PUBLIC_AUDIENCE), announce.strings("to"))
        assertEquals(listOf(AUTHOR, "https://$DOMAIN/users/alice/followers"), announce.strings("cc"))
        assertEquals(OBJECT_URI, announce["object"]?.jsonPrimitive?.content)
        val undo = AppJson.parseToJsonElement(boost.undoBody).jsonObject
        assertEquals("Undo", undo["type"]?.jsonPrimitive?.content)
        assertEquals(boost.announceId, undo["object"]?.jsonObject?.get("id")?.jsonPrimitive?.content)
        assertEquals(1, enqueuedCount)
    }

    @Test
    fun `公開タイムラインに載せない投稿は、ブーストも公開タイムラインに載せない形で送る`() = runBlocking<Unit> {
        boosts.targets = mapOf("r1" to BoostTarget.Remote(OBJECT_URI, AUTHOR, AUTHOR_INBOX, unlisted = true))

        service.setBoosted(alice, "r1", boosted = true)

        val announce = AppJson.parseToJsonElement(boosts.boosts.single().announceBody).jsonObject
        assertEquals(listOf("https://$DOMAIN/users/alice/followers"), announce.strings("to"))
        assertEquals(listOf(ActivityStreamsIri.PUBLIC_AUDIENCE, AUTHOR), announce.strings("cc"))
        assertTrue(boosts.boosts.single().unlisted)
    }

    @Test
    fun `このサーバーの投稿は、投稿の URL を指して自分の投稿でもブーストできる`() = runBlocking<Unit> {
        boosts.targets = mapOf("l1" to BoostTarget.Local(PublicNoteId("note-1"), authorUsername = "alice"))

        assertEquals(SetBoostedResult.Updated, service.setBoosted(alice, "l1", boosted = true))

        val boost = boosts.boosts.single()
        assertEquals("https://$DOMAIN/notes/note-1", boost.objectUri)
        assertEquals("https://$DOMAIN/users/alice", boost.objectAuthor)
        assertEquals(listOf(FOLLOWER_INBOX), boost.inboxes)
    }

    @Test
    fun `既にブーストしていれば積まない`() = runBlocking<Unit> {
        boosts.targets = mapOf("r1" to BoostTarget.Remote(OBJECT_URI, AUTHOR, AUTHOR_INBOX, unlisted = false))
        service.setBoosted(alice, "r1", boosted = true)

        assertEquals(SetBoostedResult.Updated, service.setBoosted(alice, "r1", boosted = true))

        assertEquals(1, enqueuedCount)
    }

    @Test
    fun `ブーストできない投稿は見つからない扱い`() = runBlocking<Unit> {
        assertEquals(SetBoostedResult.PostNotFound, service.setBoosted(alice, "r1", boosted = true))
        assertTrue(boosts.boosts.isEmpty())
    }

    @Test
    fun `外すときは今読めるかを問わずに外す`() = runBlocking<Unit> {
        assertEquals(SetBoostedResult.Updated, service.setBoosted(alice, "r1", boosted = false))
        assertEquals(listOf(alice to "r1"), boosts.unboosted)
        assertEquals(0, enqueuedCount)
    }

    @Test
    fun `ログインしていなければブーストできない`() = runBlocking<Unit> {
        assertEquals(SetBoostedResult.NotLoggedIn, service.setBoosted(null, "r1", boosted = true))
    }

    private fun JsonObject.strings(key: String): List<String> = (get(key) as? JsonArray)?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

    private object LocalAccounts : StoredActorNames {
        override fun find(username: String): String? = username.takeIf { it == "alice" }

        override fun finds(usernames: Set<String>): Map<String, String> =
            usernames.mapNotNull { username -> find(username)?.let { username to it } }.toMap()
    }

    private companion object {
        const val DOMAIN = "example.com"
        const val FOLLOWER_INBOX = "https://follower.example/inbox"
        const val AUTHOR = "https://remote.example/users/bob"
        const val AUTHOR_INBOX = "https://remote.example/users/bob/inbox"
        const val OBJECT_URI = "https://remote.example/notes/1"
        val NOW: Instant = Instant.parse("2026-11-01T00:00:00Z")
    }
}
