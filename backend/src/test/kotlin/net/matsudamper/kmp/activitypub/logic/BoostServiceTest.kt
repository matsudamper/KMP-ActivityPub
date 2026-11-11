package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.matsudamper.kmp.activitypub.FakeAccountBoostRepository
import net.matsudamper.kmp.activitypub.FakeAccountRepository
import net.matsudamper.kmp.activitypub.FakeFollowerRepository
import net.matsudamper.kmp.activitypub.repository.BoostTarget
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
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
    private val boostedPositions = mutableListOf<TimelinePosition>()
    private val service = BoostService(
        directory = ActorDirectory(domain = DOMAIN, stored = LocalAccounts),
        accountService = AccountService(accountRepository),
        boosts = boosts,
        followerRepository = followerRepository,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
        onEnqueued = { enqueuedCount++ },
        onBoosted = { boostedPositions.add(it) },
    )

    @Test
    fun `公開の投稿は公開でブーストし、投稿者とフォロワーに送って自分のタイムラインに載せる`() {
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
        assertEquals(1, boostedPositions.size)
    }

    @Test
    fun `公開タイムラインに載せない投稿は、ブーストも公開タイムラインに載せない形で送る`() {
        boosts.targets = mapOf("r1" to BoostTarget.Remote(OBJECT_URI, AUTHOR, AUTHOR_INBOX, unlisted = true))

        service.setBoosted(alice, "r1", boosted = true)

        val announce = AppJson.parseToJsonElement(boosts.boosts.single().announceBody).jsonObject
        assertEquals(listOf("https://$DOMAIN/users/alice/followers"), announce.strings("to"))
        assertEquals(listOf(ActivityStreamsIri.PUBLIC_AUDIENCE, AUTHOR), announce.strings("cc"))
        assertTrue(boosts.boosts.single().unlisted)
    }

    @Test
    fun `このサーバーの投稿は、投稿の URL を指して自分の投稿でもブーストできる`() {
        boosts.targets = mapOf("l1" to BoostTarget.Local(PublicNoteId("note-1"), authorUsername = "alice"))

        assertEquals(SetBoostedResult.Updated, service.setBoosted(alice, "l1", boosted = true))

        val boost = boosts.boosts.single()
        assertEquals("https://$DOMAIN/notes/note-1", boost.objectUri)
        assertEquals("https://$DOMAIN/users/alice", boost.objectAuthor)
        assertEquals(listOf(FOLLOWER_INBOX), boost.inboxes)
        assertEquals(1, boostedPositions.size)
    }

    @Test
    fun `既にブーストしていれば積まずにタイムラインにも載せ直さない`() {
        boosts.targets = mapOf("r1" to BoostTarget.Remote(OBJECT_URI, AUTHOR, AUTHOR_INBOX, unlisted = false))
        service.setBoosted(alice, "r1", boosted = true)

        assertEquals(SetBoostedResult.Updated, service.setBoosted(alice, "r1", boosted = true))

        assertEquals(1, enqueuedCount)
        assertEquals(1, boostedPositions.size)
    }

    @Test
    fun `ブーストできない投稿は見つからない扱い`() {
        assertEquals(SetBoostedResult.PostNotFound, service.setBoosted(alice, "r1", boosted = true))
        assertTrue(boosts.boosts.isEmpty())
    }

    @Test
    fun `外すときは今読めるかを問わずに外す`() {
        assertEquals(SetBoostedResult.Updated, service.setBoosted(alice, "r1", boosted = false))
        assertEquals(listOf(alice to "r1"), boosts.unboosted)
        assertEquals(0, enqueuedCount)
    }

    @Test
    fun `ログインしていなければブーストできない`() {
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
