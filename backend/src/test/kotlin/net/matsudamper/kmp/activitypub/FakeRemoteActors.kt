package net.matsudamper.kmp.activitypub

import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.crypto.RsaKeys
import net.matsudamper.kotpub.httpsignature.PublicKeyLookup
import net.matsudamper.kotpub.httpsignature.SignatureKey

internal class FakeRemoteActors : RemoteActors {
    private val actors = mutableMapOf<String, RemoteActor>()

    fun add(actor: RemoteActor) {
        actors[actor.actorId] = actor
    }

    override suspend fun findActor(actorId: String): RemoteActor? = actors[actorId]

    override suspend fun find(keyId: String): PublicKeyLookup {
        val actor = actors[keyId.substringBefore('#')] ?: return PublicKeyLookup.Unavailable
        return PublicKeyLookup.Found(
            SignatureKey(keyId = keyId, owner = actor.actorId, publicKey = RsaKeys.decodePublicKeyPem(actor.publicKeyPem)),
        )
    }

    override suspend fun refresh(keyId: String): PublicKeyLookup = find(keyId)

    override fun close() = Unit
}
