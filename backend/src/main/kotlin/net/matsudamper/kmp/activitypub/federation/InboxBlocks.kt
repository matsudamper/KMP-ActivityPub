package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.BlockLists
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * このサーバーのアカウント宛てに届いたものが、そのアカウントがブロックしたサーバーかアクターから来たかを見る。来ていれば受け入れない
 */
internal class InboxBlocks(
    private val accounts: AccountRepository,
    private val notes: NoteRepository,
    private val blockLists: BlockLists,
) {
    /**
     * @param username 宛先のこのサーバーのアカウント
     */
    fun blocks(username: String, actorUri: String): Boolean {
        val account = accounts.findByUsername(username) ?: return false
        return blockLists.of(account.id).hidesActor(actorUri)
    }

    /**
     * @param notePublicId お気に入りやブーストをされた、このサーバーの投稿
     */
    fun blocksOnNote(notePublicId: PublicNoteId, actorUri: String): Boolean {
        val note = notes.find(notePublicId) ?: return false
        return blocks(note.username, actorUri)
    }
}
