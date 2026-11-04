package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kotpub.entity.PublicNoteId
import net.matsudamper.kotpub.stamp.StampStore

/**
 * スタンプはまだ受け付けないので何も残さない
 */
internal object NoStamps : StampStore {
    override fun put(stamp: StampStore.ReceivedStamp): Boolean = false

    override fun remove(
        notePublicId: PublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean = false

    override fun removeActor(actorUri: String): Int = 0

    override fun findPublicKeyPem(actorUri: String): String? = null
}
