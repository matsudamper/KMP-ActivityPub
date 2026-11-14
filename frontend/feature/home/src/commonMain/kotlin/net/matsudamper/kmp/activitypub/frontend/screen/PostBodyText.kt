package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserCustomEmoji
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostMention
import net.matsudamper.kmp.activitypub.frontend.ui.TextWithEmojis

/**
 * 本文の URL とメンションを押せるようにし、カスタム絵文字を画像にする。本文は HTML を文字にしたものなので、`<a>` の情報は残っておらず URL を文字から拾う
 *
 * @param mentions 位置は [content] でのもの。範囲から外れたものは無視する
 * @param onOpenProfile acct は `user@host`
 */
internal fun createPostBodyText(
    content: String,
    mentions: List<UserPostMention>,
    emojis: List<UserCustomEmoji>,
    onOpenUrl: (url: String) -> Unit,
    onOpenProfile: (acct: String) -> Unit,
): TextWithEmojis = TextWithEmojis(
    text = buildAnnotatedString {
        val urlLinks = PlainTextUrl.findAll(content).map { match ->
            val urlRange = match.range.first until match.range.first + trimTrailingPunctuation(match.value).length
            BodyLink(range = urlRange) { onOpenUrl(content.substring(urlRange)) }
        }.toList()
        val mentionLinks = mentions
            .filter { mention -> mention.start in 0 until mention.end && mention.end <= content.length }
            .map { mention -> BodyLink(range = mention.start until mention.end) { onOpenProfile(mention.acct) } }
        // 同じ位置から始まるなら、元の HTML でリンクだったメンションを残す
        val links = (mentionLinks + urlLinks).sortedBy { it.range.first }
        val plainTextEnd = links.fold(0) { plainTextStart, link ->
            // 重なったリンクは、先に始まるほうを残す
            if (link.range.first < plainTextStart) return@fold plainTextStart
            appendWithEmojis(content.substring(plainTextStart, link.range.first), emojis)
            val linkText = content.substring(link.range)
            withLink(LinkAnnotation.Clickable(tag = linkText, styles = PostBodyLinkStyles) { link.onClick() }) {
                append(linkText)
            }
            link.range.last + 1
        }
        appendWithEmojis(content.substring(plainTextEnd), emojis)
    },
    emojis = emojis.toInlineEmojis(),
)

private class BodyLink(
    val range: IntRange,
    val onClick: () -> Unit,
)

// 文末の句読点や、URL の外側で閉じている括弧までリンクにしない
private fun trimTrailingPunctuation(url: String): String {
    // 長い句読点の連なりでも遅くならないよう、括弧は最初に一度だけ数え、削った分を差し引く
    val openParenCount = url.count { it == '(' }
    val openBracketCount = url.count { it == '[' }
    var closeParenCount = url.count { it == ')' }
    var closeBracketCount = url.count { it == ']' }
    var end = url.length
    while (end > 0) {
        val last = url[end - 1]
        val isUnbalancedClose = when (last) {
            ')' -> openParenCount < closeParenCount
            ']' -> openBracketCount < closeBracketCount
            else -> false
        }
        if (last !in TrailingPunctuation && !isUnbalancedClose) break
        when (last) {
            ')' -> closeParenCount--
            ']' -> closeBracketCount--
        }
        end--
    }
    return url.take(end)
}

// URL に使える ASCII の文字だけを拾う。日本語の本文や全角の括弧が URL に続いても、そこで切る
private val PlainTextUrl = Regex("""https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""", RegexOption.IGNORE_CASE)
private val TrailingPunctuation = setOf('.', ',', '!', '?', ':', ';', '\'', '。', '、', '」', '）')
private val PostBodyLinkStyles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))
