package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import net.matsudamper.frontend.component.HtmlFormComponents
import net.matsudamper.frontend.component.HtmlInputType

@Composable
internal actual fun SingleLineTextField(
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
    modifier: Modifier,
) {
    HtmlFormComponents.PlatformInputField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        inputId = inputId,
        inputName = inputName,
        inputType = when (kind) {
            SingleLineTextFieldKind.Plain -> HtmlInputType.Text
            SingleLineTextFieldKind.Secret -> HtmlInputType.Password
        },
        autocomplete = when (autofill) {
            null -> "off"
            SingleLineTextFieldAutofill.Username -> "username"
            SingleLineTextFieldAutofill.CurrentPassword -> "current-password"
            SingleLineTextFieldAutofill.NewPassword -> "new-password"
        },
        enabled = enabled,
        hasError = hasError,
        formId = formId,
        required = formId != null,
        modifier = modifier,
    )
}
