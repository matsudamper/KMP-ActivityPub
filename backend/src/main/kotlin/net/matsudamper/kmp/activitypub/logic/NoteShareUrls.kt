package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.note.NoteUrls

/**
 * 投稿を共有するときに渡す URL。他のサーバーの投稿は、どのサーバーからでも引ける object の URI を使う
 */
class NoteShareUrls(
    private val domain: String,
) {
    fun url(noteRef: TimelineNoteRef): String =
        when (noteRef) {
            is TimelineNoteRef.Local -> NoteUrls(domain, KotpubPublicNoteId(noteRef.publicId.value)).noteUrl
            is TimelineNoteRef.Remote -> noteRef.objectUri
        }
}
