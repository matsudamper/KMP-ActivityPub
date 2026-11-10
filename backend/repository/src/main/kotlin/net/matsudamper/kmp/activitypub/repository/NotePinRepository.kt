package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * このサーバーのアカウントがプロフィールの先頭に固定する、自分の投稿
 */
interface NotePinRepository {
    /**
     * @param maxPins 固定できる数の上限。数えてから固定するまでの間に別の固定が入って上限を超えないよう、ここで数える
     */
    fun pin(
        accountId: AccountId,
        publicId: PublicNoteId,
        pinnedAt: Instant,
        maxPins: Int,
    ): PinNoteResult

    /**
     * @return 固定していないか、[accountId] の投稿でなければ false
     */
    fun unpin(
        accountId: AccountId,
        publicId: PublicNoteId,
    ): Boolean

    /**
     * 固定した新しい順に返す
     */
    fun pinnedNotes(username: String): List<PublicNoteId>
}

sealed interface PinNoteResult {
    /**
     * 既に固定していたときも含む
     */
    data object Pinned : PinNoteResult

    /**
     * 無いか、[NotePinRepository.pin] に渡したアカウントの投稿ではない
     */
    data object NotFound : PinNoteResult

    data object TooManyPins : PinNoteResult
}
