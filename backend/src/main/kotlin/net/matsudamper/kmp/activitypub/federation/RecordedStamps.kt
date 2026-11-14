package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.repository.StampRepository
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.stamp.StampStore

internal class RecordedStamps(
    private val stampRepository: StampRepository,
    private val blocks: InboxBlocks,
) : StampStore {
    override fun put(stamp: StampStore.ReceivedStamp): Boolean {
        val notePublicId = PublicNoteId(stamp.notePublicId.value)
        if (blocks.blocksOnNote(notePublicId, stamp.actor.actorId)) return false
        return stampRepository.put(
            notePublicId = notePublicId,
            actor = stamp.actor.toRecord(),
            emoji = stamp.emoji,
            emojiImageUrl = stamp.emojiImageUrl,
            receivedAt = stamp.receivedAt,
        )
    }

    override fun remove(
        notePublicId: KotpubPublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean = stampRepository.remove(notePublicId = PublicNoteId(notePublicId.value), actorUri = actorUri, emoji = emoji)

    override fun removeActor(actorUri: String): Int = stampRepository.removeActor(actorUri)

    override fun findPublicKeyPem(actorUri: String): String? = stampRepository.findPublicKeyPem(actorUri)
}
