package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [formId] は同じフォームの [PasswordField] と揃える。パスワードマネージャーが組で扱えるようにするため
 */
@Composable
internal expect fun UsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    formId: String,
    inputId: String,
    inputName: String,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier,
)
