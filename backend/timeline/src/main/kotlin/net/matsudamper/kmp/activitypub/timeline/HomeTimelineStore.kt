package net.matsudamper.kmp.activitypub.timeline

import net.matsudamper.kmp.activitypub.repository.NotePosition
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * ホームタイムラインに並べる投稿の位置だけを持つ。正本は DB で、消えても DB から作り直せる
 */
interface HomeTimelineStore : AutoCloseable {
    /**
     * 古いものは [maxSize] 件を超えた分から捨てる
     */
    fun add(
        accountId: AccountId,
        position: NotePosition,
        maxSize: Int,
    )

    fun addAll(
        accountId: AccountId,
        positions: List<NotePosition>,
        maxSize: Int,
    )

    /**
     * 新しい順に返す
     *
     * @param after これより古いものを返す。null なら先頭から
     * @return まだ作っていなければ null
     */
    fun list(
        accountId: AccountId,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition>?
}
