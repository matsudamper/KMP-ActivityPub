package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.RemoteUser
import net.matsudamper.kmp.activitypub.logic.RemoteUserLookup
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.webfinger.WebFingerLookup

internal class KotpubRemoteUserLookup(
    private val webFinger: WebFingerLookup,
    private val remoteActors: RemoteActors,
) : RemoteUserLookup {
    override suspend fun find(
        username: String,
        host: String,
    ): RemoteUser? {
        val actorId = webFinger.findActorId("$username@$host") ?: return null
        val actor = remoteActors.findActor(actorId) ?: return null
        return RemoteUser(
            username = actor.profile.preferredUsername ?: username,
            displayName = actor.profile.displayName,
        )
    }
}
