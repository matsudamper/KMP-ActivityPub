package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
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
