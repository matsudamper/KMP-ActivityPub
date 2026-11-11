package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.repository.NotePinRepository
import net.matsudamper.kotpub.entity.PublicNoteId
import net.matsudamper.kotpub.note.FeaturedNoteStore
import net.matsudamper.kotpub.note.NoteStore
import net.matsudamper.kotpub.note.StoredNote

/**
 * プロフィールの先頭に固定した投稿を、固定した新しい順に他のサーバーへ見せる
 */
internal class RepositoryFeaturedNoteStore(
    private val pins: NotePinRepository,
    private val notes: NoteStore,
) : FeaturedNoteStore {
    override fun featured(
        username: String,
        limit: Int,
    ): List<StoredNote> {
        val pinnedNoteIds = pins.pinnedNotes(username).take(limit).map { PublicNoteId(it.value) }
        val found = notes.findByPublicIds(pinnedNoteIds.toSet())
        return pinnedNoteIds.mapNotNull { found[it] }
    }
}
