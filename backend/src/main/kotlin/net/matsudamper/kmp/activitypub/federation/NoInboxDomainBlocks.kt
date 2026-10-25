package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kotpub.inbox.InboxDomainBlocks

internal object NoInboxDomainBlocks : InboxDomainBlocks {
    override fun blocksInboxFrom(url: String): Boolean = false

    override fun signedRequestReceived(verifiedSignerActorId: String) = Unit
}
