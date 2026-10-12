package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.frontend.component.HtmlFormComponents
import net.matsudamper.frontend.component.HtmlInputType

@Composable
internal actual fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    label: String,
    formId: String,
    inputId: String,
    inputName: String,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier,
) {
    HtmlFormComponents.HiddenHtmlForm(
        formId = formId,
        onSubmit = onSubmit,
        enabled = enabled,
    )
    HtmlFormComponents.PlatformInputField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        inputId = inputId,
        inputName = inputName,
        inputType = HtmlInputType.Password,
        autocomplete = "current-password",
        enabled = enabled,
        hasError = hasError,
        formId = formId,
        required = true,
        modifier = modifier,
    )
}
