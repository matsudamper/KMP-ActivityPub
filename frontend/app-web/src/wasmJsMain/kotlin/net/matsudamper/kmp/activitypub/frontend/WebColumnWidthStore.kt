package net.matsudamper.kmp.activitypub.frontend

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.ColumnWidthStore

internal class WebColumnWidthStore : ColumnWidthStore {
    private val widthsStateFlow = MutableStateFlow(loadWidths())

    override val widthsFlow: Flow<Map<String, Float>> = widthsStateFlow.asStateFlow()

    /**
     * ストレージを拒否されたブラウザでも、開いている間だけは幅を変えられるようにする
     */
    override suspend fun save(widths: Map<String, Float>): Boolean {
        val saved = runCatching { localStorage.setItem(COLUMN_WIDTHS_KEY, Json.encodeToString(widths)) }.isSuccess
        widthsStateFlow.value = widths
        return saved
    }

    /**
     * ストレージを拒否されると getItem が例外を投げ、生成時に読むのでアプリごと起動しなくなる。
     * 壊れた値も同じく起動を止めないよう、幅を変えていない扱いにする
     */
    private fun loadWidths(): Map<String, Float> {
        val saved = runCatching { localStorage.getItem(COLUMN_WIDTHS_KEY) }.getOrNull() ?: return mapOf()
        return runCatching { Json.decodeFromString<Map<String, Float>>(saved) }.getOrDefault(mapOf())
    }
}

private const val COLUMN_WIDTHS_KEY = "columnWidths"
