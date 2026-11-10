package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserCustomEmoji
import net.matsudamper.kmp.activitypub.frontend.ui.InlineEmoji
import net.matsudamper.kmp.activitypub.frontend.ui.TextWithEmojis

internal fun createTextWithEmojis(text: String, emojis: List<UserCustomEmoji>): TextWithEmojis =
    TextWithEmojis(
        text = buildAnnotatedString { appendWithEmojis(text, emojis) },
        emojis = emojis.toInlineEmojis(),
    )

internal fun List<UserCustomEmoji>.toInlineEmojis(): List<InlineEmoji> = map { InlineEmoji(name = it.name, imageUrl = it.imageUrl) }

/**
 * [emojis] にある `:name:` を、画像を出す印に置き換えて足す。画像が出せない環境では `:name:` の文字が出る
 */
internal fun AnnotatedString.Builder.appendWithEmojis(text: String, emojis: List<UserCustomEmoji>) {
    if (emojis.isEmpty()) {
        append(text)
        return
    }
    val names = Regex(emojis.joinToString("|") { Regex.escape(it.name) })
    val end = names.findAll(text).fold(0) { start, match ->
        append(text.substring(start, match.range.first))
        appendInlineContent(id = match.value, alternateText = match.value)
        match.range.last + 1
    }
    append(text.substring(end))
}
