package net.matsudamper.kmp.activitypub.logic

import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode

internal fun htmlToPlainText(html: String): String {
    val document = Jsoup.parse(html)
    document.select("p").forEach { paragraph ->
        paragraph.before(TextNode("\n\n"))
        paragraph.after(TextNode("\n\n"))
    }
    document.select("br").forEach { lineBreak ->
        lineBreak.replaceWith(TextNode("\n"))
    }
    return document.wholeText().replace(Regex("\n{3,}"), "\n\n").trim()
}
