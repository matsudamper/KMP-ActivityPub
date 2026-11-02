package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kotpub.actor.RemoteActor

internal fun RemoteActor.toRecord(): RemoteActorRecord = RemoteActorRecord(
    actorId,
    inbox,
    sharedInbox,
    publicKeyPem,
    RemoteActorProfileRecord(profile.preferredUsername, profile.displayName, profile.profileUrl, profile.iconUrl),
)
