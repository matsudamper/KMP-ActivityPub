package net.matsudamper.kmp.activitypub.frontend.logic.language

/**
 * @param languageTag 保存したことが無く、端末の言語も有効な言語として読めないときは null
 */
data class LanguageSetting(
    val languageTag: LanguageTag?,
    val translateButtonVisible: Boolean,
)
