package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * [SingleLineTextFieldKind] の formId が同じ入力欄を、パスワードマネージャーが組で扱えるようにする
 */
@Composable
internal expect fun FormSubmitHandler(
    formId: String,
    onSubmit: () -> Unit,
    enabled: Boolean,
)
