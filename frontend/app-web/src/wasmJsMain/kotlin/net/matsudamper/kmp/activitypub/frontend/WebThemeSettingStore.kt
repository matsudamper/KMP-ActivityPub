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

    /**
     * ストレージを拒否されたブラウザでも、開いている間だけはテーマを切り替えられるようにする
     */
    override suspend fun save(themeMode: ThemeMode): Boolean {
        val saved = runCatching { localStorage.setItem(THEME_MODE_KEY, themeMode.name) }.isSuccess
        themeModeStateFlow.value = themeMode
        return saved
    }

    /**
     * ストレージを拒否されると getItem が例外を投げ、生成時に読むのでアプリごと起動しなくなる
     */
    private fun loadThemeMode(): ThemeMode {
        val saved = runCatching { localStorage.getItem(THEME_MODE_KEY) }.getOrNull()
        return ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.System
    }
}

private const val THEME_MODE_KEY = "themeMode"
