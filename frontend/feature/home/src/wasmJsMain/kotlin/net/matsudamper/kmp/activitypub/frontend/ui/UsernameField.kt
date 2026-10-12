package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.frontend.component.HtmlFormComponents
import net.matsudamper.frontend.component.HtmlInputType

@Composable
internal actual fun UsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    formId: String,
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
        inputType = HtmlInputType.Text,
        autocomplete = "username",
        enabled = enabled,
        hasError = hasError,
        formId = formId,
        required = true,
        modifier = modifier,
    )
}
