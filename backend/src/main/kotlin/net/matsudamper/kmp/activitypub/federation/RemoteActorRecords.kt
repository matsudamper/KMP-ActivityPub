package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.repository.CustomEmojiRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.emoji.CustomEmoji

internal fun RemoteActor.toRecord(): RemoteActorRecord = RemoteActorRecord(
    actorId,
    inbox,
    sharedInbox,
    publicKeyPem,
    RemoteActorProfileRecord(profile.preferredUsername, profile.displayName, profile.profileUrl, profile.iconUrl, profile.emojis.toRecords()),
)

internal fun List<CustomEmoji>.toRecords(): List<CustomEmojiRecord> = map { CustomEmojiRecord(name = it.name, imageUrl = it.imageUrl) }
