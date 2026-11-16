package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
internal actual fun MultilineTextField(
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
    modifier: Modifier,
) {
    var editedValue by remember { mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length))) }
    // 入力欄の外で本文が変わったら、カーソルを終わりに置いて合わせる
    val fieldValue = if (editedValue.text == value) editedValue else TextFieldValue(text = value, selection = TextRange(value.length))
    val currentFieldValue by rememberUpdatedState(fieldValue)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnHandleInsertion by rememberUpdatedState(onHandleInsertion)
    LaunchedEffect(insertion) {
        if (insertion == null) return@LaunchedEffect
        val inserted = insertion.insertInto(
            current = currentFieldValue.text,
            selectionStart = currentFieldValue.selection.min,
            selectionEnd = currentFieldValue.selection.max,
        )
        editedValue = TextFieldValue(text = inserted.text, selection = TextRange(inserted.caret))
        currentOnValueChange(inserted.text)
        currentOnHandleInsertion()
    }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = { changed ->
            editedValue = changed
            if (changed.text != value) onValueChange(changed.text)
        },
        enabled = enabled,
        isError = hasError,
        label = { Text(label) },
        modifier = modifier
            .heightIn(min = 160.dp)
            .onPreviewKeyEvent { event ->
                val submitShortcutPressed = onSubmitShortcut != null &&
                    event.isCtrlPressed &&
                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                if (submitShortcutPressed && event.type == KeyEventType.KeyDown) {
                    onSubmitShortcut?.invoke()
                }
                submitShortcutPressed
            },
    )
}
