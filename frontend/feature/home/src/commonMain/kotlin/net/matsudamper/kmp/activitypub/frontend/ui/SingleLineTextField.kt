package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction

/**
 * @param formId 同じ formId の [FormSubmitHandler] と入力欄を一つのフォームとして扱う
 * @param autofill null ならパスワードマネージャーに自動入力させない
 */
@Composable
internal expect fun SingleLineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    label: String,
    kind: SingleLineTextFieldKind,
    autofill: SingleLineTextFieldAutofill?,
    formId: String?,
    imeAction: ImeAction,
    inputId: String,
    inputName: String,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier,
)

internal sealed interface SingleLineTextFieldKind {
    data object Plain : SingleLineTextFieldKind

    data object Secret : SingleLineTextFieldKind
}

internal enum class SingleLineTextFieldAutofill {
    Username,
    CurrentPassword,
    NewPassword,
}
