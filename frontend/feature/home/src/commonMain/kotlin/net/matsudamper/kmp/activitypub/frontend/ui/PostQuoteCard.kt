package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 投稿の中に埋める引用先。読み込みの前後で高さが大きく変わらないよう、読み込み中も同じ枠を出す
 */
@Composable
internal fun PostQuoteCard(
    quote: PostItemUiState.Quote,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val listener = (quote as? PostItemUiState.Quote.Loaded)?.listener
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = QuoteCardMinHeight)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (listener == null) Modifier else Modifier.clickable(onClick = listener::onClick))
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        when (quote) {
            PostItemUiState.Quote.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).align(Alignment.Center),
                    strokeWidth = 2.dp,
                )
            }

            PostItemUiState.Quote.Unavailable -> {
                Text(
                    text = "引用先の投稿を表示できません",
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is PostItemUiState.Quote.Loaded -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileIcon(
                            iconUrl = quote.iconUrl,
                            contentDescription = null,
                            modifier = Modifier.size(QuoteIconSize).clip(CircleShape),
                        )
                        EmojiText(
                            modifier = Modifier.weight(1f, fill = false),
                            text = quote.displayName,
                            style = textStyle,
                            color = Color.Unspecified,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = quote.acct,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    EmojiText(
                        text = quote.body,
                        style = textStyle,
                        color = Color.Unspecified,
                        fontWeight = null,
                        maxLines = QUOTE_BODY_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val QuoteCardMinHeight = 56.dp
private val QuoteIconSize = 20.dp
private const val QUOTE_BODY_MAX_LINES = 6
