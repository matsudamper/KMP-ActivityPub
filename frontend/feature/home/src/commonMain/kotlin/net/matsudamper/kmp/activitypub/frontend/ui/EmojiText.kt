package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.launch
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
    val inlineContent = remember(text.emojis) {
        text.emojis.associate { emoji ->
            // 画像の縦横比は読み込むまで分からないので、文字に近い正方形の枠に収める
            emoji.name to InlineTextContent(
                placeholder = Placeholder(width = EmojiSize, height = EmojiSize, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
            ) {
                InlineEmojiImage(emoji = emoji)
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

/**
 * 画像だけでは何の絵文字か分からないので、マウスを重ねるか押したら :name: を上に出す。
 * 画像を読めなかったときは、文字の幅を変えないよう同じ枠に豆腐を出す
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineEmojiImage(emoji: InlineEmoji) {
    val tooltipState = rememberTooltipState()
    val coroutineScope = rememberCoroutineScope()
    var imageFailed by remember(emoji.imageUrl) { mutableStateOf(false) }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(text = emoji.name)
            }
        },
        state = tooltipState,
    ) {
        val clickModifier = Modifier
            .fillMaxSize()
            .clickable { coroutineScope.launch { tooltipState.show() } }
        if (imageFailed) {
            Box(
                modifier = clickModifier
                    .padding(1.dp)
                    .border(1.dp, LocalContentColor.current.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
            )
        } else {
            AsyncImage(
                model = emoji.imageUrl,
                contentDescription = emoji.name,
                modifier = clickModifier,
                contentScale = ContentScale.Fit,
                onError = { imageFailed = true },
            )
        }
    }
}

private val EmojiSize = 1.25.em
