package net.matsudamper.kmp.activitypub.frontend

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.matsudamper.kmp.activitypub.frontend.logic.theme.ThemeMode
import net.matsudamper.kmp.activitypub.frontend.logic.theme.ThemeSettingStore

internal class WebThemeSettingStore : ThemeSettingStore {
    private val themeModeStateFlow = MutableStateFlow(loadThemeMode())

    override val themeModeFlow: Flow<ThemeMode> = themeModeStateFlow.asStateFlow()

    override suspend fun save(themeMode: ThemeMode) {
        localStorage.setItem(THEME_MODE_KEY, themeMode.name)
        themeModeStateFlow.value = themeMode
    }

    private fun loadThemeMode(): ThemeMode {
        val saved = localStorage.getItem(THEME_MODE_KEY)
        return ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.System
    }
}

private const val THEME_MODE_KEY = "themeMode"
