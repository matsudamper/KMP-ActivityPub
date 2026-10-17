package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

@Composable
internal actual fun FormSubmitHandler(
    formId: String,
    onSubmit: () -> Unit,
    enabled: Boolean,
) = Unit
