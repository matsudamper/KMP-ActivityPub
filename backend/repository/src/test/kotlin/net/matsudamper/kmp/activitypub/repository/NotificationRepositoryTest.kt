package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class NotificationRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-notification-test")
    private val dbPath: Path = tempDir.resolve("test.db")

    @BeforeTest
    fun setUp() {
        TestSchema.applyTo(dbPath)
        listOf(AUTHOR, LOCAL_FRIEND).forEach { username ->
            Accounts.insert(
                databasePath = dbPath,
                username = username,
                passwordHash = "hash",
                passwordHashType = "hash-type",
                publicKeyPem = "public-key",
                privateKeyPem = "private-key",
                createdAt = NOW.toString(),
            )
        }
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `お気に入りとブーストは投稿ごと・種類ごとに1項目にまとまり、新しい相手が加わった順に並ぶ`() = withRepositories { repositories, author, _ ->
        val notifications = repositories.notificationRepository
        notifications.record(favourited(ALICE, NOW, null))
        notifications.record(boosted(ALICE, NOW.plusSeconds(1)))
        notifications.record(favourited(BOB, NOW.plusSeconds(2), null))

        val groups = notifications.page(author, null, 10)

        assertEquals(listOf(NotificationKind.FAVOURITE, NotificationKind.BOOST), groups.map { it.kind })
        assertEquals(listOf(BOB, ALICE), groups.first().entries.map { it.actor.actorUri })
        assertEquals(NOW.plusSeconds(2), groups.first().updatedAt)
        assertEquals(NOW, groups.first().createdAt)
        assertEquals(listOf("l1", "l1"), groups.map { it.noteMember })
        assertEquals(NOTE_1, repositories.timelineRepository.hydrate(author, listOf("l1")).single().localPublicId)
    }

    @Test
    fun `同じ相手の付け直しは絵文字だけを書き換え、並び順は変えない`() = withRepositories { repositories, author, _ ->
        val notifications = repositories.notificationRepository
        val favouriteGroup = assertNotNull(notifications.record(favourited(ALICE, NOW, null)))
        notifications.record(boosted(BOB, NOW.plusSeconds(1)))

        val reactedAt = NOW.plusSeconds(2)
        assertEquals(
            favouriteGroup,
            notifications.record(favourited(ALICE, reactedAt, NotificationReactionInput(emoji = ":blob:", emojiImageUrl = "https://remote.example/blob.png", customEmojiId = null))),
        )

        val groups = notifications.page(author, null, 10)
        assertEquals(listOf(NotificationKind.BOOST, NotificationKind.FAVOURITE), groups.map { it.kind })
        val entry = groups.last().entries.single()
        assertEquals(NotificationReaction(emoji = ":blob:", emojiImageUrl = "https://remote.example/blob.png", localCustomEmojiPublicId = null), entry.reaction)
        assertEquals(NOW, entry.createdAt)
        assertEquals(reactedAt, entry.updatedAt)
        assertEquals(NOW, groups.last().updatedAt)

        // 絵文字の無いいいねに付け直したら絵文字を外す
        notifications.record(favourited(ALICE, NOW.plusSeconds(3), null))
        assertNull(assertNotNull(notifications.find(favouriteGroup)).entries.single().reaction)
    }

    @Test
    fun `フォローとブーストは届き直しても最初の1件のまま`() = withRepositories { repositories, author, _ ->
        val notifications = repositories.notificationRepository
        assertNotNull(notifications.record(NotificationEvent.Followed(AUTHOR, NotificationActorRef.Remote(ALICE), NOW)))
        assertNotNull(notifications.record(boosted(BOB, NOW)))

        assertNull(notifications.record(NotificationEvent.Followed(AUTHOR, NotificationActorRef.Remote(ALICE), NOW.plusSeconds(1))))
        assertNull(notifications.record(boosted(BOB, NOW.plusSeconds(1))))

        val groups = notifications.page(author, null, 10)
        assertEquals(2, groups.size)
        assertTrue(groups.all { it.entries.single().createdAt == NOW && it.updatedAt == NOW })
    }

    @Test
    fun `フォローは相手ごとに1項目にする`() = withRepositories { repositories, author, _ ->
        val notifications = repositories.notificationRepository
        notifications.record(NotificationEvent.Followed(AUTHOR, NotificationActorRef.Remote(ALICE), NOW))
        notifications.record(NotificationEvent.Followed(AUTHOR, NotificationActorRef.Remote(BOB), NOW.plusSeconds(1)))

        val groups = notifications.page(author, null, 10)

        assertEquals(listOf(listOf(BOB), listOf(ALICE)), groups.map { group -> group.entries.map { it.actor.actorUri } })
        assertTrue(groups.all { it.kind == NotificationKind.FOLLOW && it.noteMember == null })
    }

    @Test
    fun `このサーバーのアカウントからの通知も残し、自分自身からは残さない`() = withRepositories { repositories, author, friend ->
        val notifications = repositories.notificationRepository
        notifications.record(NotificationEvent.Favourited(NOTE_1, null, NotificationActorRef.Local(friend), NOW))
        assertNull(notifications.record(NotificationEvent.Favourited(NOTE_1, null, NotificationActorRef.Local(author), NOW)))
        notifications.record(NotificationEvent.Mentioned(LOCAL_FRIEND, NotificationPostRef.Local(NOTE_1), NotificationActorRef.Local(author), NOW))

        val actor = notifications.page(author, null, 10).single().entries.single().actor
        assertEquals(NotificationActor(actorUri = null, username = LOCAL_FRIEND, displayName = null, iconUrl = null, emojis = listOf(), updatedAt = null), actor)
        val mention = notifications.page(friend, null, 10).single()
        assertEquals(NotificationKind.MENTION, mention.kind)
        assertEquals(AUTHOR, mention.entries.single().actor.username)
        assertEquals("l1", mention.noteMember)
    }

    @Test
    fun `続きは前のページの最後より古い項目だけを返す`() = withRepositories { repositories, author, _ ->
        val notifications = repositories.notificationRepository
        notifications.record(NotificationEvent.Followed(AUTHOR, NotificationActorRef.Remote(ALICE), NOW))
        notifications.record(NotificationEvent.Followed(AUTHOR, NotificationActorRef.Remote(BOB), NOW))
        notifications.record(favourited(ALICE, NOW, null))

        val first = notifications.page(author, null, 2)
        val second = notifications.page(author, first.last().position, 2)

        assertEquals(2, first.size)
        assertEquals(listOf(NotificationKind.FOLLOW to ALICE), second.map { it.kind to it.entries.single().actor.actorUri })
        assertTrue(notifications.page(author, second.last().position, 2).isEmpty())
    }

    @Test
    fun `取り消されても通知は残り、相手のアクターが消えたら相手の記録が消える`() = withRepositories { repositories, author, _ ->
        repositories.notificationRepository.record(NotificationEvent.Followed(AUTHOR, NotificationActorRef.Remote(ALICE), NOW))
        repositories.favouriteRepository.add(NOTE_1, actor(BOB), NOW.plusSeconds(1))
        repositories.notificationRepository.record(favourited(BOB, NOW.plusSeconds(1), null))

        repositories.favouriteRepository.remove(NOTE_1, BOB)
        assertEquals(listOf(NotificationKind.FAVOURITE, NotificationKind.FOLLOW), repositories.notificationRepository.page(author, null, 10).map { it.kind })

        // 相手が誰も残っていない項目は件数に数えず、古い項目まで読める
        repositories.favouriteRepository.removeActor(BOB)
        assertEquals(listOf(NotificationKind.FOLLOW), repositories.notificationRepository.page(author, null, 1).map { it.kind })
    }

    private fun favourited(actorUri: String, at: Instant, reaction: NotificationReactionInput?): NotificationEvent =
        NotificationEvent.Favourited(NOTE_1, reaction, NotificationActorRef.Remote(actorUri), at)

    private fun boosted(actorUri: String, at: Instant): NotificationEvent = NotificationEvent.Boosted(NOTE_1, NotificationActorRef.Remote(actorUri), at)

    private fun withRepositories(block: (Repositories, AccountId, AccountId) -> Unit) {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.deliveryQueueRepository.enqueueNote(
                NotePost(
                    username = AUTHOR,
                    requestId = "request-note-1",
                    publicId = NOTE_1,
                    contentHtml = "<p>hello</p>",
                    summary = null,
                    sourceText = "",
                    publishedAt = NOW,
                    body = "create-note-1",
                    inboxes = listOf(),
                    inReplyTo = null,
                    quoteUri = null,
                    quoteInbox = null,
                    mentions = listOf(),
                    images = listOf(),
                ),
            )
            listOf(ALICE, BOB).forEach { repositories.remoteActorRepository.save(actor(it), NOW) }
            block(
                repositories,
                checkNotNull(repositories.accountRepository.findByUsername(AUTHOR)).id,
                checkNotNull(repositories.accountRepository.findByUsername(LOCAL_FRIEND)).id,
            )
        }
    }

    private fun actor(actorUri: String): RemoteActorRecord = RemoteActorRecord(
        actorUri = actorUri,
        inbox = "$actorUri/inbox",
        sharedInbox = null,
        publicKeyPem = "key-$actorUri",
        profile = RemoteActorProfileRecord(actorUri.substringAfterLast('/'), null, null, null, null, listOf(), null, listOf()),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-12T00:00:00Z")
        val NOTE_1 = PublicNoteId("note-1")
        const val AUTHOR = "user1"
        const val LOCAL_FRIEND = "user2"
        const val ALICE = "https://remote.example/users/alice"
        const val BOB = "https://remote.example/users/bob"
    }
}
