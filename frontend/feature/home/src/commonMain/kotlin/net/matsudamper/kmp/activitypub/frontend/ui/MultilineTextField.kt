package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * @param onSubmitShortcut Ctrl+Enter で呼ばれる。null なら改行のまま
 * @param insertion カーソルの位置に入れる文字。入れたら [onHandleInsertion] を呼ぶ。入れるものが無ければ null
 * @param heightFitsContent true なら 1 行の高さから始めて、行が増えるたびに伸ばす。false なら数行分の高さを取る
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
    heightFitsContent: Boolean,
    modifier: Modifier = Modifier,
)
