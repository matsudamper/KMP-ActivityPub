package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    label: String,
    formId: String,
    inputId: String,
    inputName: String,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier,
)
