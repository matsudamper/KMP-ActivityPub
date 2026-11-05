package net.matsudamper.kmp.activitypub.frontend.logic.user

import okio.BufferedSink

/**
 * @param content 送る画像。形式はサーバーが判定する
 * @param description 画像の説明文。無ければ null
 */
class UserPostNoteImage(
    val content: UserNoteImageContent,
    val description: String?,
)

/**
 * 画像は大きいので、メモリに持たずに送るときに書き出させる
 */
interface UserNoteImageContent {
    val byteCount: Long

    fun writeTo(sink: BufferedSink)
}
