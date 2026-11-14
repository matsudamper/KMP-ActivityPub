package net.matsudamper.kmp.activitypub.logic

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode

private val PrettyPrintLeadingBreak = Regex("""^\n[ \t]*""")
private val PrettyPrintTrailingBreak = Regex("""\n[ \t]*$""")

// 平文にした後の位置を数えるため、メンションの前後に置く目印。私用領域の文字なので本文には出てこない前提で、出てきたら先に消す
private const val MENTION_START_MARKER = ''
private const val MENTION_END_MARKER = ''

internal fun htmlToPlainText(html: String): String = toPlainText(Jsoup.parse(html))

/**
 * 平文にした本文と、その中でメンションが書かれた位置を返す。
 * 押したメンションの宛先を本文の文字から探すと、別のサーバーの同じ名前の相手と取り違えるので、リンクの位置ごとに宛先を持たせる
 */
internal fun htmlToPlainTextWithMentions(html: String): PlainTextPost {
    val document = Jsoup.parse(html)
    document.traverse { node, _ ->
        if (node is TextNode) {
            node.text(node.wholeText.filterNot { it == MENTION_START_MARKER || it == MENTION_END_MARKER })
        }
    }
    val mentionAnchors = PostMentions.find(document)
    mentionAnchors.forEach { anchor ->
        anchor.element.prependChild(TextNode(MENTION_START_MARKER.toString()))
        anchor.element.appendChild(TextNode(MENTION_END_MARKER.toString()))
    }
    val markedText = toPlainText(document)

    val text = StringBuilder()
    val mentions = buildList {
        var mentionIndex = 0
        var start = 0
        markedText.forEach { char ->
            when (char) {
                MENTION_START_MARKER -> start = text.length

                MENTION_END_MARKER -> {
                    add(PlainTextMention(start = start, end = text.length, acct = mentionAnchors[mentionIndex].acct))
                    mentionIndex++
                }

                else -> text.append(char)
            }
        }
    }
    return PlainTextPost(text = text.toString(), mentions = mentions)
}

private fun toPlainText(document: Document): String {
    document.select("p").forEach { paragraph ->
        paragraph.before(TextNode("\n\n"))
        paragraph.after(TextNode("\n\n"))
    }
    val lineBreaks = document.select("br")
    // 受信時の Jsoup.clean が整形で <br> の前後に改行と字下げを足すので、そのままだと改行が二重になる。
    // <br> を置き換えた改行まで消さないよう、置き換えより先に取り除く
    lineBreaks.forEach { lineBreak ->
        val previousText = lineBreak.previousSibling() as? TextNode
        if (previousText != null) {
            previousText.text(previousText.wholeText.replace(PrettyPrintTrailingBreak, ""))
        }
        val followingText = lineBreak.nextSibling() as? TextNode
        if (followingText != null) {
            followingText.text(followingText.wholeText.replace(PrettyPrintLeadingBreak, ""))
        }
    }
    lineBreaks.forEach { lineBreak ->
        lineBreak.replaceWith(TextNode("\n"))
    }
    return document.wholeText().replace(Regex("\n{3,}"), "\n\n").trim()
}

/**
 * @param mentions [text] に書かれた順
 */
internal data class PlainTextPost(
    val text: String,
    val mentions: List<PlainTextMention>,
)

/**
 * @param start [PlainTextPost.text] での開始位置。UTF-16 の単位で数える
 * @param end 終わりの位置。この位置の文字は含まない
 * @param acct プロフィールを開くときの `user@host`
 */
internal data class PlainTextMention(
    val start: Int,
    val end: Int,
    val acct: String,
)
