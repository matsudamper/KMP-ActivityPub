package net.matsudamper.kmp.activitypub.frontend.screen

internal fun createPostContentWarningLabel(
    sensitive: Boolean,
    summary: String?,
): String? {
    if (sensitive.not()) return null
    return summary?.takeIf { it.isNotBlank() } ?: "閲覧注意"
}
