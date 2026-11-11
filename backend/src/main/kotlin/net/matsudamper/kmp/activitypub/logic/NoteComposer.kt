package net.matsudamper.kmp.activitypub.logic

/**
 * 書かれたテキストを配れる HTML に直す。HTML をそのまま受けると、任意のタグをフォロワーに配ることになる
 */
object NoteComposer {
    /**
     * Mastodon の既定の投稿長。超えた分は相手側で切られる
     */
    const val MAX_LENGTH: Int = 500

    fun compose(body: String): ComposeResult {
        val text = body.trim()
        if (text.isEmpty()) return ComposeResult.Empty
        // 書いた人の数え方と合わせるため、コードポイントで数える
        if (text.codePointCount(0, text.length) > MAX_LENGTH) return ComposeResult.TooLong

        return ComposeResult.Composed(toHtml(text))
    }

    /**
     * Mastodon が残すのはこの程度のタグで、それ以外は相手側で落とされる。
     * Mastodon は他サーバーの本文をリンクにし直さないので、URL はここで `<a>` にしておく
     */
    private fun toHtml(text: String): String =
        text
            .replace("\r\n", "\n")
            .split(Regex("\n{2,}"))
            .filter { it.isNotBlank() }
            .joinToString("") { paragraph ->
                val lines = paragraph.trim().split("\n").joinToString("<br>") { linkifyLine(it) }
                "<p>$lines</p>"
            }

    private fun linkifyLine(line: String): String =
        buildString {
            var plainStart = 0
            PlainTextUrls.find(line).forEach { urlRange ->
                append(escapeHtml(line.substring(plainStart, urlRange.first)))
                val url = escapeHtml(line.substring(urlRange))
                append("<a href=\"$url\" rel=\"nofollow noopener noreferrer\" target=\"_blank\">$url</a>")
                plainStart = urlRange.last + 1
            }
            append(escapeHtml(line.substring(plainStart)))
        }

    private fun escapeHtml(raw: String): String =
        raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    sealed interface ComposeResult {
        data class Composed(
            val contentHtml: String,
        ) : ComposeResult

        data object Empty : ComposeResult

        data object TooLong : ComposeResult
    }
}
