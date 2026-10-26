package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.matsudamper.kmp.activitypub.FakeAccountRepository
import net.matsudamper.kmp.activitypub.FakeDeliveryQueueRepository
import net.matsudamper.kmp.activitypub.FakeFollowerRepository
import net.matsudamper.kmp.activitypub.FakeImageStorage
import net.matsudamper.kmp.activitypub.FakeNoteRepository
import net.matsudamper.kmp.activitypub.delivery.DeliverySenders
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
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
    private val accountRepository = FakeAccountRepository(hasPasskey = { false })
    private val user1Id = accountRepository.add(username = "user1", passwordHash = "hash", passwordHashType = "type")
    private val followerRepository = FakeFollowerRepository()
    private val deliveryQueueRepository = FakeDeliveryQueueRepository(FakeNoteRepository())
    private val imageStorage = FakeImageStorage()
    private var enqueuedCount = 0

    private val service = createService(imageStorage = imageStorage)

    @Test
    fun `表示名を書き換えてフォロワーの inbox ごとにプロフィール全体の Update を積む`() {
        followerRepository.deliveryInboxes += listOf(INBOX_A, INBOX_B)

        assertEquals(UpdateProfileResult.Updated, service.update(username = "user1", displayName = " 新しい名前 ", icon = null))

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
        assertEquals(UpdateProfileResult.Updated, service.update(username = "user1", displayName = "  ", icon = null))

        assertNull(deliveryQueueRepository.profileUpdates.single().displayName)
    }

    @Test
    fun `長すぎる表示名は積まない`() {
        assertEquals(
            UpdateProfileResult.TooLong,
            service.update(username = "user1", displayName = "あ".repeat(ProfileUpdateService.MAX_DISPLAY_NAME_LENGTH + 1), icon = null),
        )

        assertEquals(listOf(), deliveryQueueRepository.profileUpdates)
        assertEquals(0, enqueuedCount)
    }

    @Test
    fun `アクターとして扱えないアカウントでは積まない`() {
        assertEquals(UpdateProfileResult.AccountNotFound, service.update(username = "other", displayName = "名前", icon = null))

        assertEquals(listOf(), deliveryQueueRepository.profileUpdates)
    }

    @Test
    fun `プロフィール画像を置いて Update の icon に新しい画像の URL を載せ、前の画像を消す`() {
        val oldPath = ImagePath("users/${user1Id.value}/profile-icon/old")
        imageStorage.images[oldPath] = ImageStorage.GetResult.Success(bytes = PNG, contentType = "image/png")
        accountRepository.setIconPath(user1Id, oldPath.value)

        assertEquals(UpdateProfileResult.Updated, service.update(username = "user1", displayName = "名前", icon = PNG))

        val update = deliveryQueueRepository.profileUpdates.single()
        val newPath = ImagePath(assertNotNull(update.newIconPath))
        assertTrue(newPath.value.startsWith("users/${user1Id.value}/"))
        assertEquals("image/png", imageStorage.images.getValue(newPath).contentType)
        assertFalse(oldPath in imageStorage.images)
        val actor = Json.parseToJsonElement(update.body).jsonObject.getValue("object").jsonObject
        val iconUrl = actor.getValue("icon").jsonObject.getValue("url").jsonPrimitive.content
        assertEquals("https://$DOMAIN/users/user1/icon?v=${ProfileIconPaths.version(newPath)}", iconUrl)
    }

    @Test
    fun `プロフィール画像を渡さなければ今の画像のまま`() {
        accountRepository.setIconPath(user1Id, "users/${user1Id.value}/profile-icon/current")

        assertEquals(UpdateProfileResult.Updated, service.update(username = "user1", displayName = "名前", icon = null))

        assertNull(deliveryQueueRepository.profileUpdates.single().newIconPath)
    }

    @Test
    fun `画像として読めないものは置かない`() {
        assertEquals(
            UpdateProfileResult.IconUnsupported,
            service.update(username = "user1", displayName = "名前", icon = "<svg></svg>".encodeToByteArray()),
        )

        assertEquals(mapOf(), imageStorage.images)
        assertEquals(listOf(), deliveryQueueRepository.profileUpdates)
    }

    @Test
    fun `大きすぎるプロフィール画像は置かない`() {
        val icon = PNG + ByteArray(ProfileUpdateService.MAX_ICON_BYTES)

        assertEquals(UpdateProfileResult.IconTooLarge, service.update(username = "user1", displayName = "名前", icon = icon))

        assertEquals(mapOf(), imageStorage.images)
    }

    @Test
    fun `縦か横が上限を超えるプロフィール画像は置かない`() {
        assertEquals(
            UpdateProfileResult.IconDimensionsTooLarge,
            service.update(username = "user1", displayName = "名前", icon = TestProfileIcons.read("401x3.png")),
        )
        assertEquals(
            UpdateProfileResult.IconDimensionsTooLarge,
            service.update(username = "user1", displayName = "名前", icon = TestProfileIcons.read("5x402.gif")),
        )

        assertEquals(mapOf(), imageStorage.images)
        assertEquals(listOf(), deliveryQueueRepository.profileUpdates)
    }

    @Test
    fun `寸法を読めない画像は置かない`() {
        val headerOnly = TestProfileIcons.read("400x400.png").copyOf(16)

        assertEquals(UpdateProfileResult.IconUnsupported, service.update(username = "user1", displayName = "名前", icon = headerOnly))

        assertEquals(mapOf(), imageStorage.images)
    }

    @Test
    fun `画像の置き場が無ければプロフィール画像を変えられない`() {
        val service = createService(imageStorage = null)

        assertEquals(UpdateProfileResult.ImageStorageDisabled, service.update(username = "user1", displayName = "名前", icon = PNG))

        assertEquals(listOf(), deliveryQueueRepository.profileUpdates)
    }

    private fun createService(imageStorage: ImageStorage?): ProfileUpdateService =
        ProfileUpdateService(
            senders = OnlyUser1Senders,
            accountService = AccountService(accountRepository),
            appearances = EmptyAppearances,
            profiles = StoredProfiles,
            imageStorage = imageStorage,
            followerRepository = followerRepository,
            deliveryQueueRepository = deliveryQueueRepository,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
            onEnqueued = { enqueuedCount++ },
        )

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
        val PNG: ByteArray = TestProfileIcons.read("400x400.png")
    }
}
