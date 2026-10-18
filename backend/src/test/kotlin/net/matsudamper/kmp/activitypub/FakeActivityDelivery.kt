package net.matsudamper.kmp.activitypub

import net.matsudamper.kotpub.actor.LocalActor
import net.matsudamper.kotpub.delivery.ActivityDelivery
import net.matsudamper.kotpub.delivery.DeliveryResult

internal class FakeActivityDelivery : ActivityDelivery {
    val delivered = mutableListOf<Delivered>()
    var nextResult: DeliveryResult = DeliveryResult.Delivered

    override suspend fun deliver(
        inbox: String,
        sender: LocalActor,
        body: ByteArray,
    ): DeliveryResult {
        if (nextResult == DeliveryResult.Delivered) {
            delivered += Delivered(inbox = inbox, senderActorId = sender.urls.actorId, body = body.decodeToString())
        }
        return nextResult
    }

    override fun close() = Unit

    data class Delivered(
        val inbox: String,
        val senderActorId: String,
        val body: String,
    )
}
