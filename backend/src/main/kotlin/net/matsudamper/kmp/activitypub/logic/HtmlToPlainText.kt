package net.matsudamper.kmp.activitypub.logic

import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode

internal fun htmlToPlainText(html: String): String {
    val document = Jsoup.parse(html)
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

private val PrettyPrintLeadingBreak = Regex("""^\n[ \t]*""")
private val PrettyPrintTrailingBreak = Regex("""\n[ \t]*$""")
