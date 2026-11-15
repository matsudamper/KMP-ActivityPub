package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlinx.browser.document
import net.matsudamper.frontend.component.HtmlFormComponents
import org.w3c.dom.HTMLTextAreaElement

@Composable
internal actual fun MultilineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    inputId: String,
    inputName: String,
    enabled: Boolean,
    hasError: Boolean,
    onSubmitShortcut: (() -> Unit)?,
    insertion: TextInsertion?,
    onInsertionHandled: () -> Unit,
    modifier: Modifier,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnInsertionHandled by rememberUpdatedState(onInsertionHandled)
    LaunchedEffect(insertion) {
        if (insertion == null) return@LaunchedEffect
        val textArea = document.getElementById(inputId) as? HTMLTextAreaElement
        if (textArea != null) {
            // フォーカスがある間は value を渡しても textarea に反映されないので、textarea を直接書き換える
            val inserted = insertion.insertInto(
                current = textArea.value,
                selectionStart = textArea.selectionStart ?: textArea.value.length,
                selectionEnd = textArea.selectionEnd ?: textArea.value.length,
            )
            textArea.value = inserted.text
            textArea.setSelectionRange(inserted.caret, inserted.caret)
            currentOnValueChange(inserted.text)
        }
        currentOnInsertionHandled()
    }
    HtmlFormComponents.PlatformTextArea(
        value = value,
        onValueChange = onValueChange,
        label = label,
        inputId = inputId,
        inputName = inputName,
        enabled = enabled,
        hasError = hasError,
        onSubmitShortcut = onSubmitShortcut,
        modifier = modifier,
    )
}
