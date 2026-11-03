package net.matsudamper.kmp.activitypub

import net.matsudamper.kmp.activitypub.repository.NewNoteImage
import net.matsudamper.kmp.activitypub.repository.NoteImage
import net.matsudamper.kmp.activitypub.repository.NoteImageId
import net.matsudamper.kmp.activitypub.repository.NoteImageRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 投稿に添えたかどうかは [FakeDeliveryQueueRepository] が [attach] で書く
 */
internal class FakeNoteImageRepository : NoteImageRepository {
    val added = mutableListOf<NewNoteImage>()
    private val attachedIds = mutableSetOf<NoteImageId>()

    override fun add(image: NewNoteImage): Boolean {
        added += image
        return true
    }

    override fun findUnattached(
        accountId: AccountId,
        ids: List<NoteImageId>,
    ): List<NoteImage> {
        val found =
            added
                .filter { it.accountId == accountId && it.id !in attachedIds }
                .associateBy { it.id }
        return ids.mapNotNull { found[it]?.toNoteImage() }
    }

    /**
     * @return 添えられない画像が含まれていれば何もせず false
     */
    fun attach(
        accountIds: Set<AccountId>,
        ids: List<NoteImageId>,
    ): Boolean {
        val available = added.filter { it.accountId in accountIds && it.id !in attachedIds }.map { it.id }.toSet()
        if (!available.containsAll(ids)) return false
        attachedIds += ids
        return true
    }

    fun images(ids: List<NoteImageId>): List<NoteImage> {
        val byId = added.associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toNoteImage() }
    }

    private fun NewNoteImage.toNoteImage(): NoteImage =
        NoteImage(id = id, path = path, mediaType = mediaType, description = description, width = width, height = height)
}
