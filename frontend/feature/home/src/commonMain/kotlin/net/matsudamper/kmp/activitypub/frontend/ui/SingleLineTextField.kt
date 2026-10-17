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
    onDone: () -> Unit = {},
)

internal sealed interface SingleLineTextFieldKind {
    /**
     * null 以外なら、パスワードマネージャーに自動入力させる。
     * 同じ formId の [FormSubmitHandler] と入力欄を組で扱う
     */
    val formId: String?

    data class Plain(
        override val formId: String?,
    ) : SingleLineTextFieldKind

    data class Secret(
        override val formId: String?,
    ) : SingleLineTextFieldKind
}
