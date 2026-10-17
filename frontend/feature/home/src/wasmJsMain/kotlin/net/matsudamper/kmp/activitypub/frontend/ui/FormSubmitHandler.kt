package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import net.matsudamper.frontend.component.HtmlFormComponents

@Composable
internal actual fun FormSubmitHandler(
    formId: String,
    onSubmit: () -> Unit,
    enabled: Boolean,
) {
    HtmlFormComponents.HiddenHtmlForm(
        formId = formId,
        onSubmit = onSubmit,
        enabled = enabled,
    )
}
