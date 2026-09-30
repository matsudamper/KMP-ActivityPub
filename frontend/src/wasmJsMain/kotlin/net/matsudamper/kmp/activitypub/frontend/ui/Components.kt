@file:OptIn(ExperimentalWasmJsInterop::class)

package net.matsudamper.kmp.activitypub.frontend.ui

import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.window

internal fun openExternalLink(url: String) {
    window.open(url, "_blank", "noopener,noreferrer")
}

internal fun copyToClipboard(text: String, onResult: (Boolean) -> Unit) {
    window.navigator.clipboard.writeText(text).then(
        onFulfilled = {
            onResult(true)
            null
        },
        onRejected = {
            onResult(false)
            null
        },
    )
}
