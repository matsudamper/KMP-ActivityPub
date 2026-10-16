package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun SingleLineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    inputId: String,
    inputName: String,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        isError = hasError,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}
