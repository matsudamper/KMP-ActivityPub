package net.matsudamper.kmp.activitypub.frontend.screen

object AndroidPreviewScreenPlatform : ScreenPlatform {
    override val host: String = "example.com"

    override fun openExternalLink(url: String) = Unit

    override fun copyToClipboard(text: String, onResult: (Boolean) -> Unit) = Unit
}
