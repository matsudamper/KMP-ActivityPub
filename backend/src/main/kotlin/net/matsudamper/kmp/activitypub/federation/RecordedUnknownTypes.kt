package net.matsudamper.kmp.activitypub.federation

import java.net.URI
import java.time.Clock
import net.matsudamper.kmp.activitypub.repository.UnknownObjectTypeRepository
import net.matsudamper.kotpub.note.UnknownTypeStore

/**
 * 型の名前も出どころも相手が自由に決められる文字列なので、管理画面に出せるものだけを、件数を絞って残す
 */
internal class RecordedUnknownTypes(
    private val repository: UnknownObjectTypeRepository,
    private val clock: Clock,
) : UnknownTypeStore {
    override suspend fun record(types: List<String>, sourceUrl: String) {
        val displayableTypes = types.filter { DISPLAYABLE_TYPE_NAME.matches(it) }.take(MAX_TYPES_PER_SOURCE)
        if (displayableTypes.isEmpty() || !sourceUrl.isDisplayableHttpUrl()) return
        repository.record(typeNames = displayableTypes, sourceUrl = sourceUrl, seenAt = clock.instant())
    }

    private fun String.isDisplayableHttpUrl(): Boolean {
        if (length > MAX_SOURCE_URL_LENGTH) return false
        val scheme = runCatching { URI(this).scheme }.getOrNull()?.lowercase()
        return scheme == "http" || scheme == "https"
    }

    private companion object {
        val DISPLAYABLE_TYPE_NAME = Regex("[A-Za-z0-9_:./#-]{1,64}")
        const val MAX_TYPES_PER_SOURCE = 10
        const val MAX_SOURCE_URL_LENGTH = 2048
    }
}
