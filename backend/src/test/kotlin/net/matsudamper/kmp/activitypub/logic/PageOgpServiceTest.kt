package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage
import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kmp.activitypub.timeline.AccountEvents
import net.matsudamper.kmp.activitypub.timeline.FakeTimelineStore
import net.matsudamper.kmp.activitypub.timeline.TimelineService

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class PageOgpServiceTest {
    private val domain = "example.com"
    private val repositories = FakeRepositories()
    private val timeline = TimelineService(
        repositories.timelineRepository,
        repositories.followingGroupRepository,
        BlockLists(repositories.serverBlockRepository, repositories.actorBlockRepository),
        FakeTimelineStore(mutableListOf()),
        AccountEvents(repositories.timelineRepository),
        Clock.systemUTC(),
    )
    private val iconUrl = "https://$domain/users/alice/icon?v=1"
    private val service = PageOgpService(
        domain = domain,
        accounts = repositories.accountRepository,
        timeline = timeline,
        profileIconUrls = ProfileIconUrls { username -> if (username == "alice") iconUrl else null },
        noteImageUrls = NoteImageUrls(domain),
    )
    private val image = TimelinePostImage.Local(publicId = NoteImagePublicId("img1"), description = "猫の写真", contentType = "image/png")

    @Test
    fun `このサーバーのユーザーのプロフィールはプロフィール画像を小さく出す`() {
        repositories.accountRepository.add("alice", "hash", "type")

        val ogp = service.find(listOf("@alice"))

        assertEquals(PageOgpType.Profile, ogp?.type)
        assertEquals("alice (@alice@example.com)", ogp?.title)
        assertEquals("https://example.com/@alice", ogp?.url)
        assertEquals(PageOgpImage(url = iconUrl, alt = null, large = false), ogp?.image)
    }

    @Test
    fun `他のサーバーのユーザーのプロフィールは出さない`() {
        assertNull(service.find(listOf("@bob@remote.example")))
    }

    @Test
    fun `投稿は本文と最初の静止画を出す`() {
        storePost(member = "l1", post = localPost(member = "l1", images = listOf(video(), image), sensitive = false, summary = null))

        val ogp = service.find(listOf("posts", "l1"))

        assertEquals(PageOgpType.Article, ogp?.type)
        assertEquals("本文です", ogp?.description)
        assertEquals("https://example.com/posts/l1", ogp?.url)
        assertEquals(PageOgpImage(url = "https://example.com/media/img1", alt = "猫の写真", large = true), ogp?.image)
    }

    @Test
    fun `閲覧注意の投稿は本文も添付画像も出さず、プロフィール画像にする`() {
        storePost(member = "l1", post = localPost(member = "l1", images = listOf(image), sensitive = true, summary = null))

        val ogp = service.find(listOf("posts", "l1"))

        assertEquals("閲覧注意の投稿", ogp?.description)
        assertEquals(PageOgpImage(url = iconUrl, alt = null, large = false), ogp?.image)
    }

    @Test
    fun `注意書きのある投稿は本文の代わりに注意書きを出し、添付画像を出さない`() {
        storePost(member = "l1", post = localPost(member = "l1", images = listOf(image), sensitive = false, summary = "ネタバレ"))

        val ogp = service.find(listOf("posts", "l1"))

        assertEquals("ネタバレ", ogp?.description)
        assertEquals(PageOgpImage(url = iconUrl, alt = null, large = false), ogp?.image)
    }

    @Test
    fun `他のサーバーの投稿は出さない`() {
        storePost(
            member = "r1",
            post = localPost(member = "r1", images = listOf(), sensitive = false, summary = null).copy(noteRef = TimelineNoteRef.Remote("https://remote.example/notes/1")),
        )

        assertNull(service.find(listOf("posts", "r1")))
    }

    @Test
    fun `消した投稿は出さない`() {
        storePost(member = "l1", post = localPost(member = "l1", images = listOf(), sensitive = false, summary = null).copy(deleted = true))

        assertNull(service.find(listOf("posts", "l1")))
    }

    private fun storePost(member: String, post: TimelinePost) {
        repositories.timelineRepository.storedFetchedNotes[member] = post
    }

    private fun video(): TimelinePostImage.Local =
        TimelinePostImage.Local(publicId = NoteImagePublicId("video1"), description = null, contentType = "video/mp4")

    private fun localPost(
        member: String,
        images: List<TimelinePostImage>,
        sensitive: Boolean,
        summary: String?,
    ): TimelinePost =
        TimelinePost(
            position = TimelinePosition(1, member),
            contentHtml = "<p>本文です</p>",
            language = null,
            publishedAt = Instant.EPOCH,
            actorUri = null,
            username = "alice",
            displayName = null,
            summary = summary,
            sensitive = sensitive,
            actorIconUrl = null,
            actorUpdatedAt = null,
            images = images,
            emojis = listOf(),
            actorEmojis = listOf(),
            favouriteCount = null,
            stamps = listOf(),
            likedByMe = false,
            boostedByMe = false,
            reactionMember = member,
            noteRef = TimelineNoteRef.Local(PublicNoteId(member)),
            boostCount = null,
            boostedBy = null,
            deleted = false,
            boostUndone = false,
            inReplyTo = null,
            quoteUri = null,
            editedAt = null,
            linkHref = null,
        )
}
