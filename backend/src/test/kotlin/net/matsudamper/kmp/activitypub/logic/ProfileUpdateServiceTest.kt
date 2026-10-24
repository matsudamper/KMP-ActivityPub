package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.matsudamper.kmp.activitypub.FakeDeliveryQueueRepository
import net.matsudamper.kmp.activitypub.FakeFollowerRepository
import net.matsudamper.kmp.activitypub.FakeNoteRepository
import net.matsudamper.kmp.activitypub.delivery.DeliverySenders
import net.matsudamper.kotpub.actor.ActorAppearance
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorPrivateKey
import net.matsudamper.kotpub.actor.ActorProfile
import net.matsudamper.kotpub.actor.LocalActor
import net.matsudamper.kotpub.actor.StoredActorAppearances
import net.matsudamper.kotpub.actor.StoredActorNames
import net.matsudamper.kotpub.actor.StoredActorProfiles
import net.matsudamper.kotpub.crypto.RsaKeys

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class ProfileUpdateServiceTest {
    private val followerRepository = FakeFollowerRepository()
    private val deliveryQueueRepository = FakeDeliveryQueueRepository(FakeNoteRepository())
    private var enqueuedCount = 0

    private val service =
        ProfileUpdateService(
            senders = OnlyUser1Senders,
            appearances = EmptyAppearances,
            profiles = StoredProfiles,
            followerRepository = followerRepository,
            deliveryQueueRepository = deliveryQueueRepository,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
            onEnqueued = { enqueuedCount++ },
        )

    @Test
    fun `表示名を書き換えてフォロワーの inbox ごとにプロフィール全体の Update を積む`() {
        followerRepository.deliveryInboxes += listOf(INBOX_A, INBOX_B)

        assertEquals(UpdateProfileResult.Updated, service.update(username = "user1", displayName = " 新しい名前 "))

        val update = deliveryQueueRepository.profileUpdates.single()
        assertEquals("user1", update.username)
        assertEquals("新しい名前", update.displayName)
        assertEquals(listOf(INBOX_A, INBOX_B), update.inboxes)
        val activity = Json.parseToJsonElement(update.body).jsonObject
        assertEquals("Update", activity.getValue("type").jsonPrimitive.content)
        val actor = activity.getValue("object").jsonObject
        assertEquals("新しい名前", actor.getValue("name").jsonPrimitive.content)
        // 変えていない項目も載せる
        assertTrue(actor.getValue("summary").jsonPrimitive.content.contains(STORED_SUMMARY))
        assertEquals(1, enqueuedCount)
    }

    @Test
    fun `空の表示名は未設定に戻す`() {
        assertEquals(UpdateProfileResult.Updated, service.update(username = "user1", displayName = "  "))

        assertNull(deliveryQueueRepository.profileUpdates.single().displayName)
    }

    @Test
    fun `長すぎる表示名は積まない`() {
        assertEquals(
            UpdateProfileResult.TooLong,
            service.update(username = "user1", displayName = "あ".repeat(ProfileUpdateService.MAX_DISPLAY_NAME_LENGTH + 1)),
        )

        assertEquals(listOf(), deliveryQueueRepository.profileUpdates)
        assertEquals(0, enqueuedCount)
    }

    @Test
    fun `アクターとして扱えないアカウントでは積まない`() {
        assertEquals(UpdateProfileResult.AccountNotFound, service.update(username = "other", displayName = "名前"))

        assertEquals(listOf(), deliveryQueueRepository.profileUpdates)
    }

    private object OnlyUser1Senders : DeliverySenders {
        private val directory = ActorDirectory(domain = DOMAIN, stored = OnlyUser1)
        private val privateKey = ActorPrivateKey(RsaKeys.generateKeyPair().private, ActorPrivateKey.Origin.Environment)

        override fun find(username: String): LocalActor? {
            val urls = directory.resolve(username) ?: return null
            return LocalActor(urls = urls, privateKey = privateKey)
        }
    }

    private object OnlyUser1 : StoredActorNames {
        override fun find(username: String): String? = username.takeIf { it == "user1" }

        override fun finds(usernames: Set<String>): Map<String, String> =
            usernames.mapNotNull { username -> find(username)?.let { username to it } }.toMap()
    }

    private object EmptyAppearances : StoredActorAppearances {
        override fun find(username: String): ActorAppearance = ActorAppearance.EMPTY
    }

    private object StoredProfiles : StoredActorProfiles {
        override fun find(username: String): ActorProfile = ActorProfile(displayName = "古い名前", summary = STORED_SUMMARY)
    }

    private companion object {
        const val DOMAIN = "example.com"
        const val INBOX_A = "https://a.example/inbox"
        const val INBOX_B = "https://b.example/inbox"
        const val STORED_SUMMARY = "自己紹介"
        val NOW: Instant = Instant.parse("2026-10-01T00:00:00Z")
    }
}
