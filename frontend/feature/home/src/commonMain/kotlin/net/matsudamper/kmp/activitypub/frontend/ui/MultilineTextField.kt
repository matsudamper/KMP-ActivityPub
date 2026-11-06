package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * @param onSubmitShortcut Ctrl+Enter で呼ばれる。null なら改行のまま
 */
@Composable
internal expect fun MultilineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    inputId: String,
    inputName: String,
    enabled: Boolean,
    hasError: Boolean,
    onSubmitShortcut: (() -> Unit)?,
    modifier: Modifier = Modifier,
)
