package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import net.matsudamper.kmp.activitypub.logic.RemoteUser
import net.matsudamper.kmp.activitypub.logic.RemoteUserLookup
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.webfinger.WebFingerLookup

internal class KotpubRemoteUserLookup(
    private val webFinger: WebFingerLookup,
    private val remoteActors: RemoteActors,
    private val storedActors: RemoteActorRepository,
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
        storedActors.save(
            RemoteActorRecord(
                actorUri = actor.actorId,
                inbox = actor.inbox,
                sharedInbox = actor.sharedInbox,
                publicKeyPem = actor.publicKeyPem,
                profile = RemoteActorProfileRecord(username, actor.profile.displayName, actor.profile.profileUrl, actor.profile.iconUrl),
            ),
            updatedAt = Instant.now(),
        )
        return RemoteUser(actorUri = actor.actorId, username = username, displayName = actor.profile.displayName, iconUrl = actor.profile.iconUrl)
    }
}
