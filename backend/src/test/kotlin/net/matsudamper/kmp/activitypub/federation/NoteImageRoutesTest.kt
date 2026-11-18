package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import net.matsudamper.kmp.activitypub.FakeImageStorage
import net.matsudamper.kmp.activitypub.FakeNoteRepository
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.NoteImage
import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class NoteImageRoutesTest {
    @Test
    fun `Range があれば指定の範囲だけを 206 で返す`() =
        testApplication {
            application { routing { noteImageRoutes(createFiles()) } }

            val response = client.get(MEDIA_PATH) { header(HttpHeaders.Range, "bytes=2-5") }

            assertEquals(HttpStatusCode.PartialContent, response.status)
            assertEquals("bytes 2-5/10", response.headers[HttpHeaders.ContentRange])
            assertEquals("video/mp4", response.headers[HttpHeaders.ContentType])
            assertContentEquals(BYTES.copyOfRange(2, 6), response.bodyAsBytes())
        }

    @Test
    fun `Range が無ければ全体を 200 で返し、Range を受け付けることを示す`() =
        testApplication {
            application { routing { noteImageRoutes(createFiles()) } }

            val response = client.get(MEDIA_PATH)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
            assertContentEquals(BYTES, response.bodyAsBytes())
        }

    @Test
    fun `範囲が中身の外なら 416`() =
        testApplication {
            application { routing { noteImageRoutes(createFiles()) } }

            val response = client.get(MEDIA_PATH) { header(HttpHeaders.Range, "bytes=20-30") }

            assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, response.status)
            assertEquals("bytes */10", response.headers[HttpHeaders.ContentRange])
        }

    private fun createFiles(): NoteImageFiles {
        val imageStorage = FakeImageStorage()
        imageStorage.images[ImagePath(PATH)] = ImageStorage.GetResult.Success(bytes = BYTES, contentType = "application/octet-stream")
        val noteRepository = FakeNoteRepository()
        noteRepository.notes +=
            Note(
                publicId = PublicNoteId("note"),
                username = "user1",
                contentHtml = "",
                summary = null,
                sourceText = null,
                publishedAt = Instant.parse("2026-11-01T00:00:00Z"),
                updatedAt = null,
                images = listOf(NoteImage(publicId = NoteImagePublicId(PUBLIC_ID), path = PATH, contentType = "video/mp4", description = null)),
                inReplyTo = null,
                quoteUri = null,
                quoteInbox = null,
                mentions = listOf(),
                sensitive = false,
            )
        return NoteImageFiles(noteRepository = noteRepository, imageStorage = imageStorage)
    }

    private companion object {
        const val PUBLIC_ID = "video"
        const val PATH = "2026/11/users/1/note-images/video"
        const val MEDIA_PATH = "/media/$PUBLIC_ID"
        val BYTES = ByteArray(10) { it.toByte() }
    }
}
