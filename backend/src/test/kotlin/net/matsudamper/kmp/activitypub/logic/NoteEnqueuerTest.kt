package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.IncomingFollow
import net.matsudamper.kmp.activitypub.repository.NewRemoteActor
import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfile
import net.matsudamper.kotpub.TestLocalActor
import net.matsudamper.kotpub.TestWebPageUrls
import net.matsudamper.kotpub.actor.ActorUrls
import net.matsudamper.kotpub.note.NotePublisher

class NoteEnqueuerTest {
    private val repositories = FakeRepositories()

    private val notes = RepositoryNoteStore(repositories.notes)

    private fun enqueuer(): NoteEnqueuer = NoteEnqueuer(
        publisher = NotePublisher(notes, TestWebPageUrls),
        followers = repositories.followers,
        deliveryQueue = repositories.deliveryQueue,
        domainBlocks = DomainBlockService(domainBlocks = repositories.domainBlocks, clock = { FOLLOWED_AT }),
    )

    private fun added(): List<Note> = repositories.notes.all()

    @Test
    fun `保存されているアカウントからも投函できる`() = runBlocking {
        val sender = assertNotNull(TestLocalActor.directory.resolve(TestLocalActor.STORED_USERNAME))

        enqueuer().enqueue(sender = sender, contentHtml = "<p>本文</p>")

        assertEquals(TestLocalActor.STORED_USERNAME, added().single().username)
    }

    @Test
    fun `投稿はフォロワーの inbox ごとにキューへ入り その場では送らない`() = runBlocking {
        val follower = NewRemoteActor(
            actorUri = "https://remote.example/users/follower",
            inbox = "https://remote.example/users/follower/inbox",
            sharedInbox = "https://remote.example/inbox",
            publicKeyPem = "pem",
            profile = RemoteActorProfile(preferredUsername = null, displayName = null, profileUrl = null, iconUrl = null),
        )
        repositories.followers.record(
            IncomingFollow(
                username = TestLocalActor.USERNAME,
                follower = follower,
                followActivityUri = "https://remote.example/follows/1",
                receivedAt = FOLLOWED_AT,
                acceptBody = """{"type":"Accept"}""",
            ),
        )
        // Accept が届いて初めてフォロワーになる。投函した行はここで消える
        repositories.deliveryQueue.claim(now = FOLLOWED_AT, limit = 10).forEach {
            repositories.deliveryQueue.markDelivered(id = it.id, deliveredAt = FOLLOWED_AT)
        }

        val queued = enqueuer().enqueue(sender = SENDER, contentHtml = "<p>本文</p>")

        assertEquals(1, added().size)
        // 送るのは配信ワーカー。組み立てた行が残っているだけで、まだ誰にも届いていない
        val row = repositories.deliveryQueue.rows().single()
        assertEquals("https://remote.example/inbox", row.inbox)
        assertEquals(TestLocalActor.USERNAME, row.username)
        assertContains(assertNotNull(row.body), queued.url)
    }

    @Test
    fun `配信を止めているドメインの inbox には投函しない`() = runBlocking {
        follow(actorUri = "https://blocked.example/users/a", sharedInbox = "https://blocked.example/inbox")
        follow(actorUri = "https://remote.example/users/b", sharedInbox = "https://remote.example/inbox")
        repositories.domainBlocks.markUnavailable(domain = "blocked.example", description = "諦めた", at = FOLLOWED_AT)

        enqueuer().enqueue(sender = SENDER, contentHtml = "<p>本文</p>")

        assertEquals(listOf("https://remote.example/inbox"), repositories.deliveryQueue.rows().map { it.inbox })
    }

    private fun follow(
        actorUri: String,
        sharedInbox: String,
    ) {
        repositories.followers.record(
            IncomingFollow(
                username = TestLocalActor.USERNAME,
                follower = NewRemoteActor(
                    actorUri = actorUri,
                    inbox = "$actorUri/inbox",
                    sharedInbox = sharedInbox,
                    publicKeyPem = "pem",
                    profile = RemoteActorProfile(preferredUsername = null, displayName = null, profileUrl = null, iconUrl = null),
                ),
                followActivityUri = "$actorUri/follows/1",
                receivedAt = FOLLOWED_AT,
                acceptBody = """{"type":"Accept"}""",
            ),
        )
        repositories.deliveryQueue.claim(now = FOLLOWED_AT, limit = 10).forEach {
            repositories.deliveryQueue.markDelivered(id = it.id, deliveredAt = FOLLOWED_AT)
        }
    }

    private companion object {
        val SENDER: ActorUrls = TestLocalActor.urls
        val FOLLOWED_AT: Instant = Instant.parse("2026-08-16T00:00:00Z")
    }
}
