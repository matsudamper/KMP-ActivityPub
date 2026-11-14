package net.matsudamper.kmp.activitypub.logic

/**
 * 書かれたテキストを配れる HTML に直す。HTML をそのまま受けると、任意のタグをフォロワーに配ることになる
 */
object NoteComposer {
    /**
     * Mastodon の既定の投稿長。超えた分は相手側で切られる
     */
    const val MAX_LENGTH: Int = 500

    /**
     * @param mentions 本文に書かれていたら、宛てたアカウントへのリンクにする。書かれていなくても宛先からは外さない
     */
    fun compose(
        body: String,
        mentions: List<MentionLink>,
    ): ComposeResult {
        val text = body.trim()
        if (text.isEmpty()) return ComposeResult.Empty
        // 書いた人の数え方と合わせるため、コードポイントで数える
        if (text.codePointCount(0, text.length) > MAX_LENGTH) return ComposeResult.TooLong

        return ComposeResult.Composed(toHtml(text, mentions))
    }

    /**
     * Mastodon が残すのはこの程度のタグで、それ以外は相手側で落とされる。
     * Mastodon は他サーバーの本文をリンクにし直さないので、URL はここで `<a>` にしておく
     */
    private fun toHtml(
        text: String,
        mentions: List<MentionLink>,
    ): String =
        text
            .replace("\r\n", "\n")
            .split(Regex("\n{2,}"))
            .filter { it.isNotBlank() }
            .joinToString("") { paragraph ->
                val lines = paragraph.trim().split("\n").joinToString("<br>") { linkifyLine(it, mentions) }
                "<p>$lines</p>"
            }

    private fun linkifyLine(
        line: String,
        mentions: List<MentionLink>,
    ): String {
        val urlRanges = PlainTextUrls.find(line).map { LinkRange(it, mention = null) }.toList()
        val mentionRanges =
            mentions.flatMap { mention -> findMention(line, mention.text).map { LinkRange(it, mention) } }
                .filter { mentionRange -> urlRanges.none { it.range.overlaps(mentionRange.range) } }
        return buildString {
            var plainStart = 0
            (urlRanges + mentionRanges).sortedBy { it.range.first }.forEach { link ->
                if (link.range.first < plainStart) return@forEach
                append(escapeHtml(line.substring(plainStart, link.range.first)))
                val mention = link.mention
                if (mention == null) {
                    val url = escapeHtml(line.substring(link.range))
                    append("<a href=\"$url\" rel=\"nofollow noopener noreferrer\" target=\"_blank\">$url</a>")
                } else {
                    // Mastodon が出すメンションと同じ形にすると、相手側でも同じ見た目になる
                    append(
                        "<span class=\"h-card\" translate=\"no\"><a href=\"${escapeHtml(mention.href)}\" class=\"u-url mention\">" +
                            "@<span>${escapeHtml(mention.username)}</span></a></span>",
                    )
                }
                plainStart = link.range.last + 1
            }
            append(escapeHtml(line.substring(plainStart)))
        }
    }

    /**
     * `@alice` が `@alice@example.com` や `@alice_2` の一部のときは別のアカウントなので、前後が名前に続く文字なら当てない
     */
    private fun findMention(
        line: String,
        mentionText: String,
    ): List<IntRange> =
        Regex("(?<![\\w@.-])${Regex.escape(mentionText)}(?![\\w@-]|\\.\\w)", RegexOption.IGNORE_CASE)
            .findAll(line)
            .map { it.range }
            .toList()

    private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last

    private fun escapeHtml(raw: String): String =
        raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    /**
     * @param text 本文に書かれる形。`@alice@example.com` など
     * @param href 宛てたアカウントのプロフィールの URL
     * @param username リンクに出す、`@` を付けない名前
     */
    data class MentionLink(
        val text: String,
        val href: String,
        val username: String,
    )

    /**
     * @param mention URL なら null
     */
    private class LinkRange(
        val range: IntRange,
        val mention: MentionLink?,
    )

    sealed interface ComposeResult {
        data class Composed(
            val contentHtml: String,
        ) : ComposeResult

        data object Empty : ComposeResult

        data object TooLong : ComposeResult
    }
}
