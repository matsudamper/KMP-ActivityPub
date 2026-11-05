package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.note.NoteUrls

/**
 * このサーバーの投稿のパーマリンク。他のサーバーに配る Note の id と同じものにする
 */
class LocalNoteUrls(
    private val domain: String,
) {
    fun url(publicId: PublicNoteId): String = NoteUrls(domain, KotpubPublicNoteId(publicId.value)).noteUrl
}
