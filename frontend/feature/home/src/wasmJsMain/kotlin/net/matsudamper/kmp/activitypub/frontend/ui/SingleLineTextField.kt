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
    onImeAction: () -> Unit,
    label: String,
    kind: SingleLineTextFieldKind,
    autofill: SingleLineTextFieldAutofill?,
    formId: String?,
    required: Boolean,
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
        required = required,
        enterKeyHint = imeAction.toEnterKeyHint(),
        // formId があれば Enter で form が送信され、FormSubmitHandler が受ける
        onEnter = if (formId == null && imeAction.isSubmitAction()) onImeAction else null,
        modifier = modifier,
    )
}

private fun ImeAction.toEnterKeyHint(): String? =
    when (this) {
        ImeAction.Done -> "done"
        ImeAction.Next -> "next"
        ImeAction.Previous -> "previous"
        ImeAction.Go -> "go"
        ImeAction.Search -> "search"
        ImeAction.Send -> "send"
        else -> null
    }

private fun ImeAction.isSubmitAction(): Boolean =
    when (this) {
        ImeAction.Done, ImeAction.Go, ImeAction.Search, ImeAction.Send -> true
        else -> false
    }
