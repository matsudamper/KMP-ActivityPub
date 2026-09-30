package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.activitypub.actor.RemoteActor
import net.matsudamper.activitypub.actor.RemoteActorProfile
import net.matsudamper.kmp.activitypub.repository.NewRemoteActor
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfile as StoredRemoteActorProfile

internal object StoredRemoteActors {
    fun of(actor: RemoteActor): NewRemoteActor = NewRemoteActor(
        actorUri = actor.actorId,
        inbox = actor.inbox,
        sharedInbox = actor.sharedInbox,
        publicKeyPem = actor.publicKeyPem,
        profile = of(actor.profile),
    )

    fun of(profile: RemoteActorProfile): StoredRemoteActorProfile = StoredRemoteActorProfile(
        preferredUsername = profile.preferredUsername,
        displayName = profile.displayName,
        profileUrl = profile.profileUrl,
        iconUrl = profile.iconUrl,
    )
}
