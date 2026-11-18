package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.BlockedActor
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost

class PostSearchServiceTest {
    private val repositories = FakeRepositories()
    private val alice = repositories.accountRepository.add("alice", "hash", "type")
    private val service = PostSearchService(
        timeline = repositories.timelineRepository,
        blockLists = BlockLists(serverBlocks = repositories.serverBlockRepository, actorBlocks = repositories.actorBlockRepository),
    )

    @Test
    fun keywordIsMatchedAgainstDisplayedTextIgnoringCase() {
        add("r1", """<p>Hello <a href="https://example.com/span">World</a></p>""")

        assertEquals(listOf("r1"), search("hello world"))
        assertEquals(listOf("r1"), search("WORLD"))
        // タグの名前や属性の値では見つからない
        assertEquals(listOf(), search("span"))
        assertEquals(listOf(), search("href"))
    }

    @Test
    fun everyKeywordMustBeContained() {
        add("r1", "<p>りんご と みかん</p>")
        add("r2", "<p>りんご</p>")

        assertEquals(listOf("r1"), search("みかん　りんご"))
    }

    @Test
    fun blankQueryFindsNothing() {
        add("r1", "<p>りんご</p>")

        assertEquals(listOf(), search("  "))
    }

    @Test
    fun blockedAuthorsPostsAreNotFound() {
        add("r1", "<p>りんご</p>")
        repositories.actorBlockRepository.add(alice, BlockedActor(actorUri = ACTOR, acct = "carol@remote.example"), Instant.EPOCH)

        assertEquals(listOf(), search("りんご"))
    }

    @Test
    fun fullPageHasCursorToTheRest() {
        (1..25).forEach { index -> add("r$index", "<p>りんご</p>", publishedAt = Instant.EPOCH.plusSeconds(100L - index)) }

        val first = service.search(viewer = alice, query = "りんご", cursor = null)
        val cursor = assertNotNull(PostSearchCursor.decode(assertNotNull(first.nextCursor).encode()))
        val second = service.search(viewer = alice, query = "りんご", cursor = cursor)

        assertEquals((1..20).map { "r$it" }, first.posts.map { it.position.member })
        assertEquals((21..25).map { "r$it" }, second.posts.map { it.position.member })
        assertNull(second.nextCursor)
    }

    private fun search(query: String): List<String> =
        service.search(viewer = alice, query = query, cursor = null).posts.map { it.position.member }

    private fun add(member: String, contentHtml: String, publishedAt: Instant = Instant.EPOCH) {
        repositories.timelineRepository.searchablePosts.add(
            TimelinePost(
                position = TimelinePosition(1, member),
                contentHtml = contentHtml,
                language = null,
                publishedAt = publishedAt,
                actorUri = ACTOR,
                username = "carol",
                displayName = "Carol",
                summary = null,
                sensitive = false,
                actorIconUrl = null,
                actorUpdatedAt = null,
                images = listOf(),
                favouriteCount = null,
                stamps = listOf(),
                likedByMe = false,
                boostedByMe = null,
                reactionMember = member,
                noteRef = TimelineNoteRef.Remote("https://remote.example/notes/$member"),
                boostCount = null,
                boostedBy = null,
                actorEmojis = listOf(),
                emojis = listOf(),
                deleted = false,
                boostUndone = false,
                inReplyTo = null,
                quoteUri = null,
                editedAt = null,
                linkHref = null,
                unknownTypes = listOf(),
            ),
        )
    }

    private companion object {
        const val ACTOR = "https://remote.example/users/carol"
    }
}
