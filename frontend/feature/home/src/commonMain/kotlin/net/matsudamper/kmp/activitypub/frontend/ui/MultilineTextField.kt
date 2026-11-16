package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * @param onSubmitShortcut Ctrl+Enter で呼ばれる。null なら改行のまま
 * @param insertion カーソルの位置に入れる文字。入れたら [onHandleInsertion] を呼ぶ。入れるものが無ければ null
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
    insertion: TextInsertion?,
    onHandleInsertion: () -> Unit,
    modifier: Modifier = Modifier,
)
