package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable

@Composable
internal actual fun PostBodySelectionContainer(content: @Composable () -> Unit) {
    SelectionContainer(content = content)
}
