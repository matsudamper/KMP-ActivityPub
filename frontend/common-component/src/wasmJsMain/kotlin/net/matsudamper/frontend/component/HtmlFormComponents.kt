@file:OptIn(ExperimentalWasmJsInterop::class)

package net.matsudamper.frontend.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js
import kotlinx.browser.document
import kotlinx.browser.window
import androidx.compose.material3.TextFieldDefaults as MaterialTextFieldDefaults
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLFormElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

enum class HtmlInputType(val value: String) {
    Text("text"),
    Password("password"),
    Email("email"),
    Search("search"),
    Tel("tel"),
    Url("url"),
}

object HtmlFormComponents {
    /**
     * プラットフォームの入力欄を Material3 の TextField 風に見せる。
     *
     * Web では HTML の input を埋め込み、オートフィルとクリップボード操作を
     * ブラウザに任せる。
     */
    @OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun PlatformInputField(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        inputId: String,
        inputName: String,
        inputType: HtmlInputType,
        autocomplete: String,
        enabled: Boolean,
        hasError: Boolean,
        modifier: Modifier = Modifier,
        formId: String? = null,
        required: Boolean = false,
    ) {
        val currentOnValueChange = rememberUpdatedState(onValueChange)
        val colors = MaterialTheme.colorScheme
        val textStyle = MaterialTheme.typography.bodyLarge
        val interactionSource = remember { MutableInteractionSource() }
        val focusInteractionHolder = remember { FocusInteractionHolder() }
        val clickInteractionSource = remember { MutableInteractionSource() }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = clickInteractionSource,
                    indication = null,
                    enabled = enabled,
                ) {
                    (document.getElementById(inputId) as? HTMLInputElement)?.focus()
                },
        ) {
            MaterialTextFieldDefaults.DecorationBox(
                value = value,
                visualTransformation = VisualTransformation.None,
                innerTextField = {
                    HtmlElementView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(InputLineHeight),
                        factory = {
                            val input = document.createElement("input") as HTMLInputElement
                            input.id = inputId
                            input.name = inputName
                            input.type = inputType.value
                            input.autocomplete = autocomplete
                            if (formId != null) {
                                input.setAttribute("form", formId)
                            }
                            input.setAttribute("aria-label", label)
                            input.required = required
                            bindDomListeners(
                                input = input,
                                onValueChange = { currentOnValueChange.value(it) },
                            )
                            input
                        },
                        update = { input ->
                            input.disabled = !enabled
                            applyInputStyle(
                                input = input,
                                fontSizeSp = textStyle.fontSize.value,
                                textColor = colors.onSurface,
                            )
                            bindFocusHandlers(
                                input = input,
                                interactionSource = interactionSource,
                                focusInteractionHolder = focusInteractionHolder,
                                onValueChange = { currentOnValueChange.value(it) },
                            )
                            syncInputValue(input = input, value = value)
                        },
                    )
                },
                label = {
                    Text(label)
                },
                placeholder = null,
                leadingIcon = null,
                trailingIcon = null,
                prefix = null,
                suffix = null,
                supportingText = null,
                shape = MaterialTextFieldDefaults.shape,
                singleLine = true,
                enabled = enabled,
                isError = hasError,
                interactionSource = interactionSource,
                colors = MaterialTextFieldDefaults.colors(
                    focusedSupportingTextColor = colors.surface,
                    focusedContainerColor = colors.surface,
                    focusedIndicatorColor = colors.primary,
                    focusedTextColor = colors.onSurface,
                    focusedLabelColor = colors.primary,
                    focusedPrefixColor = colors.onSurfaceVariant,
                    focusedSuffixColor = colors.onSurfaceVariant,
                    focusedPlaceholderColor = colors.onSurfaceVariant,
                    focusedTrailingIconColor = colors.onSurfaceVariant,
                    focusedLeadingIconColor = colors.onSurfaceVariant,
                ),
            )
        }
    }

    /**
     * 複数行の [PlatformInputField]。HTML の textarea を埋め込む
     */
    @OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun PlatformTextArea(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        inputId: String,
        inputName: String,
        enabled: Boolean,
        hasError: Boolean,
        modifier: Modifier = Modifier,
    ) {
        val currentOnValueChange = rememberUpdatedState(onValueChange)
        val colors = MaterialTheme.colorScheme
        val textStyle = MaterialTheme.typography.bodyLarge
        val interactionSource = remember { MutableInteractionSource() }
        val focusInteractionHolder = remember { FocusInteractionHolder() }
        val clickInteractionSource = remember { MutableInteractionSource() }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = clickInteractionSource,
                    indication = null,
                    enabled = enabled,
                ) {
                    (document.getElementById(inputId) as? HTMLTextAreaElement)?.focus()
                },
        ) {
            MaterialTextFieldDefaults.DecorationBox(
                value = value,
                visualTransformation = VisualTransformation.None,
                innerTextField = {
                    HtmlElementView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TextAreaHeight),
                        factory = {
                            val textArea = document.createElement("textarea") as HTMLTextAreaElement
                            textArea.id = inputId
                            textArea.name = inputName
                            textArea.setAttribute("aria-label", label)
                            textArea.style.setProperty("resize", "none")
                            bindDomListeners(
                                input = textArea,
                                onValueChange = { currentOnValueChange.value(it) },
                            )
                            textArea
                        },
                        update = { textArea ->
                            textArea.disabled = !enabled
                            applyInputStyle(
                                input = textArea,
                                fontSizeSp = textStyle.fontSize.value,
                                textColor = colors.onSurface,
                            )
                            bindFocusHandlers(
                                input = textArea,
                                interactionSource = interactionSource,
                                focusInteractionHolder = focusInteractionHolder,
                                onValueChange = { currentOnValueChange.value(it) },
                            )
                            syncInputValue(input = textArea, value = value)
                        },
                    )
                },
                label = {
                    Text(label)
                },
                placeholder = null,
                leadingIcon = null,
                trailingIcon = null,
                prefix = null,
                suffix = null,
                supportingText = null,
                shape = MaterialTextFieldDefaults.shape,
                singleLine = false,
                enabled = enabled,
                isError = hasError,
                interactionSource = interactionSource,
                colors = MaterialTextFieldDefaults.colors(
                    focusedSupportingTextColor = colors.surface,
                    focusedContainerColor = colors.surface,
                    focusedIndicatorColor = colors.primary,
                    focusedTextColor = colors.onSurface,
                    focusedLabelColor = colors.primary,
                    focusedPrefixColor = colors.onSurfaceVariant,
                    focusedSuffixColor = colors.onSurfaceVariant,
                    focusedPlaceholderColor = colors.onSurfaceVariant,
                    focusedTrailingIconColor = colors.onSurfaceVariant,
                    focusedLeadingIconColor = colors.onSurfaceVariant,
                ),
            )
        }
    }

    /**
     * 画面外の HTML form。オートフィルと Enter 送信のため。
     *
     * [PlatformInputField] の `formId` と同じ id を渡す。
     */
    @Composable
    fun HiddenHtmlForm(
        formId: String,
        onSubmit: () -> Unit,
        enabled: Boolean,
    ) {
        val currentOnSubmit = rememberUpdatedState(onSubmit)
        DisposableEffect(formId, enabled) {
            val form = document.getElementById(formId) as HTMLFormElement?
                ?: run {
                    val created = document.createElement("form") as HTMLFormElement
                    created.id = formId
                    created.setAttribute("autocomplete", "on")
                    created.style.display = "none"
                    // 入力欄が 2 つ以上ある form は、送信ボタンが無いと Enter で送信されない
                    val submitButton = document.createElement("button")
                    submitButton.setAttribute("type", "submit")
                    created.appendChild(submitButton)
                    document.body?.appendChild(created)
                    created
                }
            form.onsubmit = { event ->
                event.preventDefault()
                if (enabled) {
                    currentOnSubmit.value()
                }
            }
            onDispose {
                form.onsubmit = null
            }
        }
    }
}

private class FocusInteractionHolder {
    var focus: FocusInteraction.Focus? = null
}

private fun bindDomListeners(
    input: HTMLElement,
    onValueChange: (String) -> Unit,
) {
    // oninput プロパティの型は (InputEvent) -> Unit。切り取りやパスワードマネージャーの
    // 自動入力では素の Event が来るので、キャストせず addEventListener で受ける。
    input.addEventListener("input") { event ->
        onValueChange(readInputValue(event))
    }
    input.addEventListener("change") { event ->
        onValueChange(readInputValue(event))
    }
    input.addEventListener("keydown") { event ->
        if (isClipboardShortcutKey(event)) {
            event.stopPropagation()
            when (keyboardKey(event).lowercase()) {
                "x" -> {
                    if (cutSelection(input, onValueChange)) {
                        event.preventDefault()
                    }
                }

                "c" -> {
                    if (copySelection(input)) {
                        event.preventDefault()
                    }
                }

                "a" -> {
                    selectAll(input)
                    event.preventDefault()
                }
            }
        }
    }
}

private fun cutSelection(
    input: HTMLElement,
    onValueChange: (String) -> Unit,
): Boolean {
    val start = selectionStart(input)
    val end = selectionEnd(input)
    if (start < 0 || start >= end) return false
    val current = elementValue(input)
    copyToClipboard(current.substring(start, end))
    val next = current.removeRange(start, end)
    setElementValue(input, next)
    setSelectionRange(input, start, start)
    onValueChange(next)
    return true
}

private fun copySelection(input: HTMLElement): Boolean {
    val start = selectionStart(input)
    val end = selectionEnd(input)
    if (start < 0 || start >= end) return false
    copyToClipboard(elementValue(input).substring(start, end))
    return true
}

private fun syncInputValue(
    input: HTMLElement,
    value: String,
) {
    if (document.activeElement == input) return
    if (elementValue(input) != value) {
        setElementValue(input, value)
    }
}

private fun isClipboardShortcutKey(event: Event): Boolean {
    if (event.type != "keydown") return false
    if (!hasClipboardModifier(event)) return false
    return when (keyboardKey(event).lowercase()) {
        "x", "c", "v", "a" -> true
        else -> false
    }
}

private fun hasClipboardModifier(event: Event): Boolean =
    js("event.ctrlKey === true || event.metaKey === true")

private fun keyboardKey(event: Event): String =
    js("String(event.key || '')")

private fun bindFocusHandlers(
    input: HTMLElement,
    interactionSource: MutableInteractionSource,
    focusInteractionHolder: FocusInteractionHolder,
    onValueChange: (String) -> Unit,
) {
    input.onfocus = {
        if (focusInteractionHolder.focus == null) {
            val focus = FocusInteraction.Focus()
            focusInteractionHolder.focus = focus
            interactionSource.tryEmit(focus)
        }
    }
    input.onblur = {
        val focus = focusInteractionHolder.focus
        if (focus != null) {
            interactionSource.tryEmit(FocusInteraction.Unfocus(focus))
            focusInteractionHolder.focus = null
        }
        onValueChange(elementValue(input))
    }
}

private fun applyInputStyle(
    input: HTMLElement,
    fontSizeSp: Float,
    textColor: Color,
) {
    input.style.apply {
        boxSizing = "border-box"
        display = "block"
        width = "100%"
        height = "100%"
        margin = "0"
        padding = "0"
        border = "none"
        outline = "none"
        backgroundColor = "transparent"
        color = textColor.toCssColor()
        setProperty("appearance", "none")
        setProperty("-webkit-appearance", "none")
        fontSize = "${fontSizeSp}px"
        lineHeight = "normal"
        fontFamily = "inherit"
        letterSpacing = "normal"
        setProperty("user-select", "text")
        setProperty("-webkit-user-select", "text")
    }
}

private fun readInputValue(event: Event): String {
    val target = event.target as? HTMLElement ?: return ""
    return elementValue(target)
}

// input と textarea は value や選択範囲を同じ名前で持つが、Kotlin の型では共通の親に無い
private fun elementValue(element: HTMLElement): String =
    js("String(element.value || '')")

private fun setElementValue(
    element: HTMLElement,
    value: String,
) {
    js("element.value = value")
}

private fun selectionStart(element: HTMLElement): Int =
    js("element.selectionStart ?? -1")

private fun selectionEnd(element: HTMLElement): Int =
    js("element.selectionEnd ?? -1")

private fun setSelectionRange(
    element: HTMLElement,
    start: Int,
    end: Int,
) {
    js("element.setSelectionRange(start, end)")
}

private fun selectAll(element: HTMLElement) {
    js("element.select()")
}

private fun copyToClipboard(text: String) {
    window.navigator.clipboard.writeText(text)
}

private fun Color.toCssColor(): String {
    val argb = toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "rgb($r, $g, $b)"
}

private val InputLineHeight = 24.dp

private val TextAreaHeight = 120.dp
