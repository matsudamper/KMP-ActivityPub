package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.DeletedRemoteNote
import net.matsudamper.kmp.activitypub.repository.DeletedRemoteNoteRepository
import net.matsudamper.kmp.activitypub.shared.DeletedRemoteNoteId
import org.slf4j.LoggerFactory

/**
 * 投稿者が消した他のサーバーの投稿を管理画面で見て、保存から消す
 */
class DeletedRemoteNoteService(
    private val repository: DeletedRemoteNoteRepository,
) {
    private val logger = LoggerFactory.getLogger(DeletedRemoteNoteService::class.java)

    /**
     * 受け取った順の新しい順
     *
     * @param after 前回の [DeletedRemoteNotePage.nextCursor]。最初のページなら null
     * @return [after] が読めなければ null
     */
    fun page(after: String?, limit: Int): DeletedRemoteNotePage? {
        val beforeId = if (after == null) null else after.toLongOrNull() ?: return null
        val pageSize = limit.coerceIn(1, MAX_PAGE_SIZE)
        val notes = repository.page(beforeId = beforeId, limit = pageSize)
        return DeletedRemoteNotePage(
            notes = notes,
            nextCursor = notes.lastOrNull()?.takeIf { notes.size == pageSize }?.id?.toString(),
        )
    }

    /**
     * @return 消された投稿として保存していなければ false
     */
    fun purge(id: DeletedRemoteNoteId): Boolean {
        val purged = repository.purge(id.value)
        if (purged) logger.info("消された他のサーバーの投稿を保存から消した: ${id.value}")
        return purged
    }

    /**
     * @return 消した数
     */
    fun purgeAll(): Int {
        val count = repository.purgeAll()
        logger.info("消された他のサーバーの投稿を全て保存から消した: $count 件")
        return count
    }

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}

/**
 * @param nextCursor 続きが無ければ null
 */
data class DeletedRemoteNotePage(
    val notes: List<DeletedRemoteNote>,
    val nextCursor: String?,
)
