package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.repository.UnknownObjectType
import net.matsudamper.kmp.activitypub.repository.UnknownObjectTypeRepository

class RecordedUnknownTypesTest {
    @Test
    fun onlyDisplayableTypeNamesAreRecorded() = runBlocking {
        val repository = RecordingRepository()
        val types = listOf("Article", "as:Page", "with space", "<script>", "a".repeat(65)) + (1..20).map { "Type$it" }

        RecordedUnknownTypes(repository, CLOCK).record(types, SOURCE)

        assertEquals(listOf(Triple(listOf("Article", "as:Page") + (1..8).map { "Type$it" }, SOURCE, NOW)), repository.recorded)
    }

    @Test
    fun nonHttpSourceIsNotRecorded() = runBlocking {
        val repository = RecordingRepository()

        RecordedUnknownTypes(repository, CLOCK).record(listOf("Article"), "javascript:alert(1)")
        RecordedUnknownTypes(repository, CLOCK).record(listOf("Article"), "https://remote.example/" + "a".repeat(2048))
        RecordedUnknownTypes(repository, CLOCK).record(listOf("<script>"), SOURCE)

        assertTrue(repository.recorded.isEmpty())
    }

    private class RecordingRepository : UnknownObjectTypeRepository {
        val recorded = mutableListOf<Triple<List<String>, String, Instant>>()

        override fun record(typeNames: List<String>, sourceUrl: String, seenAt: Instant) {
            recorded.add(Triple(typeNames, sourceUrl, seenAt))
        }

        override fun page(beforeId: Long?, limit: Int): List<UnknownObjectType> = listOf()
    }

    private companion object {
        const val SOURCE = "https://remote.example/objects/1"
        val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    }
}
