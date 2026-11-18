package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import net.matsudamper.kotpub.note.NoteVisibility
import net.matsudamper.kotpub.note.ReceivedNote

class ReceivedNoteToRemoteNoteInputTest {
    @Test
    fun linkHrefIsPutAtTopOfContentAsAnchor() {
        val input = note(content = "", linkHref = "https://example.com/?a=1&b=2").toRemoteNoteInput(localRecipients = setOf(), now = Instant.EPOCH)
        assertEquals("https://example.com/?a=1&b=2", input.linkHref)
        assertEquals(
            """<p><a href="https://example.com/?a=1&amp;b=2" rel="nofollow">https://example.com/?a=1&amp;b=2</a></p>""",
            input.contentHtml,
        )
    }

    @Test
    fun noteKeepsContentAsIs() {
        val input = note(content = "<p>本文</p>", linkHref = null).toRemoteNoteInput(localRecipients = setOf(), now = Instant.EPOCH)
        assertEquals(null, input.linkHref)
        assertEquals("<p>本文</p>", input.contentHtml)
    }

    private fun note(content: String, linkHref: String?): ReceivedNote = ReceivedNote(
        id = "https://remote.example/links/1",
        attributedTo = "https://remote.example/actor",
        content = content,
        language = null,
        published = Instant.EPOCH,
        updated = null,
        to = listOf(),
        cc = listOf(),
        inReplyTo = null,
        quote = null,
        summary = null,
        sensitive = false,
        visibility = NoteVisibility.Public,
        attachments = listOf(),
        emojis = listOf(),
        mentions = listOf(),
        poll = null,
        linkHref = linkHref,
    )
}
