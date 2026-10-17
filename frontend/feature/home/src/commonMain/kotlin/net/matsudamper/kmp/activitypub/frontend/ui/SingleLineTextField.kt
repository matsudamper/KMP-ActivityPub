package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction

@Composable
internal expect fun SingleLineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    kind: SingleLineTextFieldKind,
    imeAction: ImeAction,
    inputId: String,
    inputName: String,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier,
)

internal sealed interface SingleLineTextFieldKind {
    /**
     * @param formId 同じフォームの [PasswordField] と揃える。パスワードマネージャーが組で扱えるようにするため
     */
    data class Username(
        val formId: String,
    ) : SingleLineTextFieldKind

    /**
     * パスワードマネージャーに自動入力させない
     */
    data object Plain : SingleLineTextFieldKind
}
