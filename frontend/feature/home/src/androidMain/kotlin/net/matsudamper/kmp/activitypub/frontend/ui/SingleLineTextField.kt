package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
internal actual fun SingleLineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        isError = hasError,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = when (kind) {
            SingleLineTextFieldKind.Plain -> VisualTransformation.None
            SingleLineTextFieldKind.Secret -> PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = when (kind) {
                SingleLineTextFieldKind.Plain -> KeyboardType.Text
                SingleLineTextFieldKind.Secret -> KeyboardType.Password
            },
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = if (autofill == null) {
            modifier
        } else {
            modifier.semantics { contentType = autofill.toContentType() }
        },
    )
}

private fun SingleLineTextFieldAutofill.toContentType(): ContentType =
    when (this) {
        SingleLineTextFieldAutofill.Username -> ContentType.Username
        SingleLineTextFieldAutofill.CurrentPassword -> ContentType.Password
        SingleLineTextFieldAutofill.NewPassword -> ContentType.NewPassword
    }
