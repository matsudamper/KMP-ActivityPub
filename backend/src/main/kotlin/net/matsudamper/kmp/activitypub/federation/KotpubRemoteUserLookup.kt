package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.RemoteUser
import net.matsudamper.kmp.activitypub.logic.RemoteUserLookup
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.webfinger.WebFingerLookup

internal class KotpubRemoteUserLookup(
    private val webFinger: WebFingerLookup,
    private val remoteActors: RemoteActors,
) : RemoteUserLookup {
    override suspend fun findByAcct(
        username: String,
        host: String,
    ): RemoteUser? {
        val actorId = webFinger.findActorId("$username@$host") ?: return null
        return findByUrl(actorId)
    }

    /**
     * acct のユーザー名は preferredUsername から決まるので、無いアクターは返さない
     */
    override suspend fun findByUrl(url: String): RemoteUser? {
        val actor = remoteActors.findActor(url) ?: return null
        val username = actor.profile.preferredUsername ?: return null
        return RemoteUser(username = username, displayName = actor.profile.displayName, iconUrl = actor.profile.iconUrl)
    }
}
