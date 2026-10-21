package net.matsudamper.kmp.activitypub.timeline

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.NotePosition
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * タイムラインの位置を文字列にする。文字列の大小が (時刻, 公開 id) の順と一致するよう、時刻の桁を固定する。
 * Valkey の Sorted Set の member と、画面に渡す続きの位置の両方に使う
 */
object TimelineCursor {
    private const val EPOCH_SECOND_DIGITS = 12
    private const val NANO_DIGITS = 9
    private const val SEPARATOR = ':'
    private const val PUBLIC_ID_START = EPOCH_SECOND_DIGITS + NANO_DIGITS + 1

    fun encode(position: NotePosition): String {
        val epochSecond = position.publishedAt.epochSecond.toString().padStart(EPOCH_SECOND_DIGITS, '0')
        val nano = position.publishedAt.nano.toString().padStart(NANO_DIGITS, '0')
        return "$epochSecond$nano$SEPARATOR${position.publicId.value}"
    }

    /**
     * @return 形が違えば null
     */
    fun decode(value: String): NotePosition? {
        if (value.length <= PUBLIC_ID_START || value[PUBLIC_ID_START - 1] != SEPARATOR) return null
        val epochSecond = value.substring(0, EPOCH_SECOND_DIGITS).toLongOrNull() ?: return null
        val nano = value.substring(EPOCH_SECOND_DIGITS, PUBLIC_ID_START - 1).toLongOrNull() ?: return null
        if (epochSecond < 0 || nano < 0) return null
        return NotePosition(
            publishedAt = Instant.ofEpochSecond(epochSecond, nano),
            publicId = PublicNoteId(value.substring(PUBLIC_ID_START)),
        )
    }
}
