package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.NotePinRepository
import net.matsudamper.kmp.activitypub.repository.PinNoteResult
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * 自分の投稿をプロフィールの先頭に固定する。固定はこのサーバーの中だけで見せ、他のサーバーには配らない
 */
class NotePinService(
    private val accounts: AccountRepository,
    private val pins: NotePinRepository,
    private val clock: Clock,
) {
    /**
     * 既に望んだ状態なら何もせずに成功する
     */
    fun setPinned(
        accountId: AccountId?,
        publicId: PublicNoteId,
        pinned: Boolean,
    ): SetPinnedResult {
        if (accountId == null) return SetPinnedResult.NotLoggedIn
        // セッションが残っていてもアカウントが消えていることがある
        val account = accounts.findById(accountId) ?: return SetPinnedResult.NotLoggedIn
        if (!pinned) {
            pins.unpin(account.id, publicId)
            return SetPinnedResult.Updated
        }
        return when (pins.pin(account.id, publicId, pinnedAt = clock.instant(), maxPins = MAX_PINS)) {
            PinNoteResult.Pinned -> SetPinnedResult.Updated
            PinNoteResult.NotFound -> SetPinnedResult.NotFound
            PinNoteResult.TooManyPins -> SetPinnedResult.TooManyPins
        }
    }

    companion object {
        /**
         * Mastodon の固定できる投稿の上限
         */
        const val MAX_PINS: Int = 5
    }
}

sealed interface SetPinnedResult {
    data object Updated : SetPinnedResult

    data object NotLoggedIn : SetPinnedResult

    /**
     * 無いか、ログイン中のアカウントの投稿ではない
     */
    data object NotFound : SetPinnedResult

    data object TooManyPins : SetPinnedResult
}
