package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage

/**
 * カスタム絵文字を画像で出す文字列。[text] の inline content の印の id は [InlineEmoji.name] にする
 */
@Immutable
data class TextWithEmojis(
    val text: AnnotatedString,
    val emojis: List<InlineEmoji>,
)

/**
 * @param name `:name:` の形。inline content の印の id と、画像の代わりの文字に使う
 */
data class InlineEmoji(
    val name: String,
    val imageUrl: String,
)

@Composable
fun EmojiText(
    text: TextWithEmojis,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight?,
    maxLines: Int,
    overflow: TextOverflow,
    modifier: Modifier = Modifier,
) {
    var failedEmojiNames by remember(text.emojis) { mutableStateOf(setOf<String>()) }
    // inline content を渡さなかった印には :name: の文字が出るので、画像を読めなかった絵文字は外す
    val inlineContent = remember(text.emojis, failedEmojiNames) {
        text.emojis.filter { it.name !in failedEmojiNames }.associate { emoji ->
            // 画像の縦横比は読み込むまで分からないので、文字に近い正方形の枠に収める
            emoji.name to InlineTextContent(
                placeholder = Placeholder(width = EmojiSize, height = EmojiSize, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
            ) {
                AsyncImage(
                    model = emoji.imageUrl,
                    contentDescription = emoji.name,
                    contentScale = ContentScale.Fit,
                    onError = { failedEmojiNames = failedEmojiNames + emoji.name },
                )
            }
        }
    }
    Text(
        text = text.text,
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = inlineContent,
    )
}

private val EmojiSize = 1.25.em
