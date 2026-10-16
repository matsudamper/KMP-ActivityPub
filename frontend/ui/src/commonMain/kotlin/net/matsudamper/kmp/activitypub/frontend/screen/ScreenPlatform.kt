package net.matsudamper.kmp.activitypub.frontend.screen

interface ScreenPlatform {
    val host: String

    fun openExternalLink(url: String)

    fun copyToClipboard(text: String, onResult: (Boolean) -> Unit)
}
