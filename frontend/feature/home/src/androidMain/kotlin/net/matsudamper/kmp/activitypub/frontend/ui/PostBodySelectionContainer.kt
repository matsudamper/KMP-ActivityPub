package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 投稿全体が押せるので、長押しの選択と競合させない
 */
@Composable
internal actual fun PostBodySelectionContainer(
    contentPadding: PaddingValues,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.padding(contentPadding)) {
        content()
    }
}
