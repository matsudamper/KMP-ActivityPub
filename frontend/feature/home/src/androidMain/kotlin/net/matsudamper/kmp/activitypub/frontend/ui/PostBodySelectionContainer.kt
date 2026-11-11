package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * 投稿全体が押せるので、長押しの選択と競合させない
 */
@Composable
internal actual fun PostBodySelectionContainer(content: @Composable () -> Unit) {
    content()
}
