package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import net.matsudamper.kmp.activitypub.logic.RemoteUser
import net.matsudamper.kmp.activitypub.logic.RemoteUserLookup
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.actor.RemoteActorLookup
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
    ): RemoteUser? = findWithActorByAcct(username, host)?.user

    override suspend fun findByUrl(url: String): RemoteUser? = findWithActorByUrl(url)?.user

    /**
     * [findByAcct] と同じく引き、取得したアクター文書も返す。プロフィールの表示に要る部分を、同じアクターを取り直さずに読める
     */
    suspend fun findWithActorByAcct(
        username: String,
        host: String,
    ): FoundRemoteUser? {
        val actorId =
            when (val result = webFinger.findActorId("$username@$host")) {
                is WebFingerResult.Found -> result.actorId

                is WebFingerResult.NotFound,
                WebFingerResult.InvalidAcct,
                -> return null

                is WebFingerResult.Unavailable -> {
                    logger.warn("WebFinger を引けなかった: $username@$host ${result.failure}")
                    return null
                }
            }
        return findWithActorByUrl(actorId)
    }

    /**
     * [findByUrl] と同じく引き、取得したアクター文書も返す。
     * acct のユーザー名は preferredUsername から決まるので、無いアクターは返さない
     */
    suspend fun findWithActorByUrl(url: String): FoundRemoteUser? {
        val actor = when (val lookup = remoteActors.findActorByUrl(url)) {
            is RemoteActorLookup.Found -> lookup.actor

            RemoteActorLookup.Gone -> return null

            is RemoteActorLookup.Unavailable -> {
                logger.warn("アクターを取れなかった: $url ${lookup.failure}")
                return null
            }
        }
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
        val user = RemoteUser(
            actorUri = actor.actorId,
            username = username,
            displayName = actor.profile.displayName,
            iconUrl = actor.profile.iconUrl,
            displayNameEmojis = emojis,
        )
        return FoundRemoteUser(user = user, actor = actor)
    }
}

internal data class FoundRemoteUser(
    val user: RemoteUser,
    val actor: RemoteActor,
)
