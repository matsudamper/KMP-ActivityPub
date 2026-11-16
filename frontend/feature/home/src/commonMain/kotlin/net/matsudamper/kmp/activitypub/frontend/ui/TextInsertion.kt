package net.matsudamper.kmp.activitypub.frontend.ui

/**
 * 入力欄のカーソルの位置に入れる文字。カーソルの位置は入力欄しか知らないので、入れるのは入力欄
 *
 * @param id 入れるたびに変える。同じ文字を続けて入れても別のものとして扱う
 * @param separatedFromWords 前後を空白で区切る。`:shortcode:` は文字に続いていると絵文字として扱われない
 */
data class TextInsertion(
    val id: String,
    val text: String,
    val separatedFromWords: Boolean,
)

/**
 * @param caret 入れた後にカーソルを置く位置
 */
internal data class InsertedText(
    val text: String,
    val caret: Int,
)

/**
 * 選んでいる範囲があれば、入れる文字で置き換える
 */
internal fun TextInsertion.insertInto(current: String, selectionStart: Int, selectionEnd: Int): InsertedText {
    val start = selectionStart.coerceIn(0, current.length)
    val end = selectionEnd.coerceIn(start, current.length)
    val before = current.substring(0, start)
    val inserted = if (separatedFromWords) {
        val leadingSpace = if (before.isEmpty() || before.last().isWhitespace()) "" else " "
        "$leadingSpace$text "
    } else {
        text
    }
    return InsertedText(text = before + inserted + current.substring(end), caret = before.length + inserted.length)
}
