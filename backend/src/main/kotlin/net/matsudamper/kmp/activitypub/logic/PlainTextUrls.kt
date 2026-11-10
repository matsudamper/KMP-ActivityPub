package net.matsudamper.kmp.activitypub.logic

/**
 * 文字のまま書かれた http / https の URL を見つける
 */
internal object PlainTextUrls {
    /**
     * 日本語の文では URL のすぐ後に全角の括弧や句読点を挟んで文章が続くので、それらで区切る。
     * `https://ja.wikipedia.org/wiki/日本` のようにパスに日本語を書く URL もあるので、全角文字すべては区切りにしない
     */
    private val candidate = Regex("""https?://[^\s<>"（）「」『』【】〈〉《》。、，．！？：；]+""", RegexOption.IGNORE_CASE)

    private val TRAILING_PUNCTUATION = setOf('.', ',', '!', '?', ':', ';', '\'')

    fun find(text: String): Sequence<IntRange> =
        candidate.findAll(text).map { match ->
            val url = trimTrailingPunctuation(match.value)
            match.range.first until match.range.first + url.length
        }

    /**
     * 文末の句読点や閉じ括弧、URL を囲む `'` が URL に続けて書かれることが多いので、末尾からは外す。
     * `https://en.wikipedia.org/wiki/Kotlin_(programming_language)` のように URL の中で開いた括弧を
     * 閉じているものは URL の一部なので残す
     */
    private fun trimTrailingPunctuation(url: String): String {
        var end = url.length
        while (end > 0) {
            val last = url[end - 1]
            val isUnbalancedClose = when (last) {
                ')' -> url.take(end).count { it == '(' } < url.take(end).count { it == ')' }
                ']' -> url.take(end).count { it == '[' } < url.take(end).count { it == ']' }
                else -> false
            }
            if (last !in TRAILING_PUNCTUATION && !isUnbalancedClose) break
            end--
        }
        return url.take(end)
    }
}
