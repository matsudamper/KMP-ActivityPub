package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.repository.StampRepository
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kmp.activitypub.timeline.TimelineService
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.stamp.StampStore

internal class RecordedStamps(
    private val stampRepository: StampRepository,
    private val blocks: InboxBlocks,
    private val timeline: TimelineService,
) : StampStore {
    override fun put(stamp: StampStore.ReceivedStamp): Boolean {
        val notePublicId = PublicNoteId(stamp.notePublicId.value)
        if (blocks.blocksOnNote(notePublicId, stamp.actor.actorId)) return false
        val put = stampRepository.put(
            notePublicId = notePublicId,
            actor = stamp.actor.toRecord(),
            emoji = stamp.emoji,
            emojiImageUrl = stamp.emojiImageUrl,
            receivedAt = stamp.receivedAt,
        )
        if (put) timeline.publishLocalNoteChanged(notePublicId)
        return put
    }

    override fun remove(
        notePublicId: KotpubPublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean {
        val publicId = PublicNoteId(notePublicId.value)
        val removed = stampRepository.remove(notePublicId = publicId, actorUri = actorUri, emoji = emoji)
        if (removed) timeline.publishLocalNoteChanged(publicId)
        return removed
    }

    override fun removeActor(actorUri: String): Int = stampRepository.removeActor(actorUri)

    override fun findPublicKeyPem(actorUri: String): String? = stampRepository.findPublicKeyPem(actorUri)
}
