package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import org.slf4j.LoggerFactory

/**
 * 置いたまま期限までに投稿に付かなかった画像を消す
 */
class UploadedNoteImageCleaner(
    private val noteRepository: NoteRepository,
    private val imageStorage: ImageStorage,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(UploadedNoteImageCleaner::class.java)

    suspend fun run() {
        while (true) {
            val hasMore =
                try {
                    cleanExpired()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("期限切れの画像を消せなかった", e)
                    false
                }
            if (!hasMore) delay(INTERVAL)
        }
    }

    /**
     * 記録を先に消し、消せたものだけ置き場から消す。間に投稿に付いた画像は記録が消えないので残る
     *
     * @return まだ残っていそうなら true
     */
    fun cleanExpired(): Boolean {
        val now = clock.instant()
        val expired = noteRepository.listExpiredUploadedImages(now = now, limit = BATCH_SIZE)
        expired.forEach { uploaded ->
            if (!noteRepository.deleteExpiredUploadedImage(publicId = uploaded.image.publicId, now = now)) return@forEach
            val request = ImageStorage.DeleteRequest(ownerAccountId = uploaded.ownerAccountId, path = ImagePath(uploaded.image.path))
            val result = imageStorage.delete(request)
            if (result is ImageStorage.DeleteResult.Failure) {
                // 記録はもう無いので、置き場に使われない画像が残るだけ
                logger.warn("期限切れの画像を置き場から消せなかった: ${uploaded.image.path}", result.cause)
            }
        }
        if (expired.isNotEmpty()) logger.info("投稿に付かなかった画像 ${expired.size} 件を消した")
        return expired.size == BATCH_SIZE
    }

    private companion object {
        val INTERVAL = 10.minutes
        const val BATCH_SIZE = 100
    }
}
