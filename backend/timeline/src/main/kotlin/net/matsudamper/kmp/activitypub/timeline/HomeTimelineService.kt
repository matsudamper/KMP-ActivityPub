package net.matsudamper.kmp.activitypub.timeline

import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.NotePosition
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * ホームタイムラインは投稿時に並べておき、読むときは並びを [HomeTimelineStore] から引いて本文だけを DB から引く。
 * フォローはまだ無いので、並ぶのは自分の投稿だけ
 *
 * @param maxSize [HomeTimelineStore] に持たせる件数。これより古いページは DB から引く
 */
class HomeTimelineService(
    private val store: HomeTimelineStore,
    private val noteRepository: NoteRepository,
    private val maxSize: Int,
) {
    fun onNotePosted(
        authorId: AccountId,
        position: NotePosition,
    ) {
        store.add(accountId = authorId, position = position, maxSize = maxSize)
    }

    /**
     * 新しい順に返す
     *
     * @param after これより古いものを返す。null なら先頭から
     */
    fun list(
        account: Account,
        after: NotePosition?,
        limit: Int,
    ): HomeTimelinePage {
        val stored = store.list(accountId = account.id, after = after, limit = limit) ?: rebuild(account, after, limit)
        // 持たせる件数を超えた古い分と、作り直す前に投稿だけ足されて歯抜けになった分は DB にしか無い
        val older =
            if (stored.size < limit) {
                noteRepository.listPositions(username = account.username, after = stored.lastOrNull() ?: after, limit = limit - stored.size)
            } else {
                listOf()
            }
        val positions = stored + older
        val notes = noteRepository.findByPublicIds(positions.map { it.publicId }.toSet())
        return HomeTimelinePage(
            // 消された投稿は並びに残っていても本文が無いので落とす
            notes = positions.mapNotNull { notes[it.publicId] },
            next = positions.lastOrNull()?.takeIf { positions.size == limit },
        )
    }

    private fun rebuild(
        account: Account,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> {
        val latest = noteRepository.listPositions(username = account.username, after = null, limit = maxSize)
        store.addAll(accountId = account.id, positions = latest, maxSize = maxSize)
        return store.list(accountId = account.id, after = after, limit = limit).orEmpty()
    }
}

/**
 * @param next 続きを引くときに渡す位置。続きが無ければ null
 */
data class HomeTimelinePage(
    val notes: List<Note>,
    val next: NotePosition?,
)
