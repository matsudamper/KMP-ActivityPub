package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/**
 * 本文の URL を押せるようにする。本文は HTML を文字にしたものなので、`<a>` の情報は残っておらず URL を文字から拾う
 */
internal fun createPostBodyText(
    content: String,
    onOpenUrl: (url: String) -> Unit,
): AnnotatedString =
    buildAnnotatedString {
        val urlRanges = PlainTextUrl.findAll(content).map { match ->
            match.range.first until match.range.first + trimTrailingPunctuation(match.value).length
        }
        val plainTextEnd = urlRanges.fold(0) { plainTextStart, urlRange ->
            append(content.substring(plainTextStart, urlRange.first))
            val url = content.substring(urlRange)
            withLink(LinkAnnotation.Clickable(tag = url, styles = PostBodyLinkStyles) { onOpenUrl(url) }) {
                append(url)
            }
            urlRange.last + 1
        }
        append(content.substring(plainTextEnd))
    }

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

private val PlainTextUrl = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
private val TrailingPunctuation = setOf('.', ',', '!', '?', ':', ';', '。', '、', '」', '）')
private val PostBodyLinkStyles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))
