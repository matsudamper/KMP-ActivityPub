package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import net.matsudamper.kmp.activitypub.logic.RemoteUser
import net.matsudamper.kmp.activitypub.logic.RemoteUserLookup
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.webfinger.WebFingerLookup
import net.matsudamper.kotpub.webfinger.WebFingerResult
import org.slf4j.LoggerFactory

internal class KotpubRemoteUserLookup(
    private val webFinger: WebFingerLookup,
    private val remoteActors: RemoteActors,
    private val storedActors: RemoteActorRepository,
) : RemoteUserLookup {
    private val logger = LoggerFactory.getLogger(KotpubRemoteUserLookup::class.java)

    override suspend fun findByAcct(
        username: String,
        host: String,
    ): RemoteUser? {
        val actorId =
            when (val result = webFinger.findActorId("$username@$host")) {
                is WebFingerResult.Found -> result.actorId
                WebFingerResult.NotFound,
                WebFingerResult.InvalidAcct,
                -> return null
                WebFingerResult.Unavailable -> {
                    logger.warn("WebFinger を引けなかった: $username@$host")
                    return null
                }
            }
        return findByUrl(actorId)
    }

    /**
     * acct のユーザー名は preferredUsername から決まるので、無いアクターは返さない
     */
    override suspend fun findByUrl(url: String): RemoteUser? {
        val actor = remoteActors.findActorByUrl(url) ?: return null
        if (url != actor.actorId) storedActors.remove(url)
        val username = actor.profile.preferredUsername ?: return null
        val emojis = actor.profile.emojis.toRecords()
        storedActors.save(
            RemoteActorRecord(
                actorUri = actor.actorId,
                inbox = actor.inbox,
                sharedInbox = actor.sharedInbox,
                publicKeyPem = actor.publicKeyPem,
                profile = RemoteActorProfileRecord(username, actor.profile.displayName, actor.profile.profileUrl, actor.profile.iconUrl, actor.profile.headerUrl, emojis),
            ),
            updatedAt = Instant.now(),
        )
        return RemoteUser(
            actorUri = actor.actorId,
            username = username,
            displayName = actor.profile.displayName,
            iconUrl = actor.profile.iconUrl,
            displayNameEmojis = emojis,
        )
    }
}
