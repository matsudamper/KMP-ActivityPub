package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.frontend.component.HiddenHtmlForm
import net.matsudamper.frontend.component.HtmlInputType
import net.matsudamper.frontend.component.PlatformInputField

@Composable
actual fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    label: String,
    formId: String,
    inputId: String,
    inputName: String,
    purpose: PasswordPurpose,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier,
) {
    HiddenHtmlForm(
        formId = formId,
        onSubmit = onSubmit,
        enabled = enabled,
    )
    PlatformInputField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        inputId = inputId,
        inputName = inputName,
        inputType = HtmlInputType.Password,
        autocomplete = when (purpose) {
            PasswordPurpose.Current -> "current-password"
            PasswordPurpose.New -> "new-password"
        },
        enabled = enabled,
        hasError = hasError,
        formId = formId,
        required = true,
        modifier = modifier,
    )
}
