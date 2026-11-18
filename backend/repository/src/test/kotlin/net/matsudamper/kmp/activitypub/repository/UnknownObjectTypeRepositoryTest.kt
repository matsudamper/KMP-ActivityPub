package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class UnknownObjectTypeRepositoryTest {
    @Test
    fun sameTypeAndSourceAreCountedInOneRow() = withRepository { unknownTypes ->
        unknownTypes.record(listOf("Article", "PropertyValue"), SOURCE, FIRST)
        unknownTypes.record(listOf("Article"), SOURCE, LATER)

        val page = unknownTypes.page(beforeId = null, limit = 10)

        assertEquals(listOf("PropertyValue", "Article"), page.map { it.typeName })
        val article = page.single { it.typeName == "Article" }
        assertEquals(2, article.seenCount)
        assertEquals(FIRST, article.firstSeenAt)
        assertEquals(LATER, article.lastSeenAt)
        assertEquals(1, page.single { it.typeName == "PropertyValue" }.seenCount)
    }

    @Test
    fun pageContinuesBeforeTheGivenId() = withRepository { unknownTypes ->
        listOf("First", "Second", "Third").forEach { unknownTypes.record(listOf(it), SOURCE, FIRST) }

        val firstPage = unknownTypes.page(beforeId = null, limit = 2)

        assertEquals(listOf("Third", "Second"), firstPage.map { it.typeName })
        assertEquals(listOf("First"), unknownTypes.page(beforeId = firstPage.last().id, limit = 2).map { it.typeName })
    }

    private fun withRepository(block: (UnknownObjectTypeRepository) -> Unit) {
        val directory = createTempDirectory("unknown-object-type-test")
        try {
            val path = directory.resolve("test.db")
            TestSchema.applyTo(path)
            Repositories.create(DatabaseConfig(path)).use { repositories ->
                block(repositories.unknownObjectTypeRepository)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val SOURCE = "https://remote.example/objects/1"
        val FIRST: Instant = Instant.parse("2026-11-01T12:00:00Z")
        val LATER: Instant = Instant.parse("2026-11-02T12:00:00Z")
    }
}
