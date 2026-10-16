package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.frontend.component.HtmlFormComponents
import net.matsudamper.frontend.component.HtmlInputType

@Composable
internal actual fun SingleLineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
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
        autocomplete = "off",
        enabled = enabled,
        hasError = hasError,
        modifier = modifier,
    )
}
