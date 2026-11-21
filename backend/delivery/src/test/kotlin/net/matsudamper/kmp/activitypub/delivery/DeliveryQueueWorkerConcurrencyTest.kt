package net.matsudamper.kmp.activitypub.delivery

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import io.opentelemetry.api.OpenTelemetry
import net.matsudamper.kmp.activitypub.repository.ClaimedDelivery
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.EnqueueNoteResult
import net.matsudamper.kmp.activitypub.repository.NoteDeletion
import net.matsudamper.kmp.activitypub.repository.NotePost
import net.matsudamper.kmp.activitypub.repository.NoteUpdate
import net.matsudamper.kmp.activitypub.repository.ProfileUpdate
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kotpub.actor.ActorPrivateKey
import net.matsudamper.kotpub.actor.ActorUrls
import net.matsudamper.kotpub.actor.LocalActor
import net.matsudamper.kotpub.crypto.RsaKeys
import net.matsudamper.kotpub.delivery.ActivityDelivery
import net.matsudamper.kotpub.delivery.DeliveryResult

class DeliveryQueueWorkerConcurrencyTest {
    @Test
    fun slowInboxHostDoesNotHoldBackOtherHosts() = runBlocking {
        val releaseSlow = CompletableDeferred<Unit>()
        val otherDelivered = CompletableDeferred<Unit>()
        val queue = Queue(
            List(BATCH) { row(id = it.toLong(), inbox = "https://slow.example/inbox") } +
                row(id = 100, inbox = "https://other.example/inbox"),
        )
        val worker = worker(queue) { inbox ->
            if (inbox.startsWith("https://slow.example/")) releaseSlow.await() else otherDelivered.complete(Unit)
        }

        val running = launch { worker.run() }
        try {
            withTimeout(5000) { otherDelivered.await() }
        } finally {
            releaseSlow.complete(Unit)
            running.cancelAndJoin()
        }
    }

    @Test
    fun sameInboxHostIsDeliveredOneAtATime() = runBlocking {
        val inFlight = Collections.synchronizedList(mutableListOf<Int>())
        var current = 0
        val queue = Queue(List(5) { row(id = it.toLong(), inbox = "https://same.example/users/$it/inbox") })
        val worker = worker(queue) {
            synchronized(inFlight) { inFlight.add(++current) }
            delay(10)
            synchronized(inFlight) { current-- }
        }

        worker.deliverDue()

        assertEquals(List(5) { 1 }, inFlight.toList())
        assertEquals(5, queue.delivered.size)
    }

    @Test
    fun sameInboxHostIsDeliveredInClaimedOrder() = runBlocking {
        val deliveredInboxes = Collections.synchronizedList(mutableListOf<String>())
        val queue = Queue(List(10) { row(id = it.toLong(), inbox = "https://same.example/users/$it/inbox") })
        val worker = worker(queue) { inbox ->
            // 後に取り出した行ほど早く終わるようにしても、取り出した順に送る
            delay((10 - inbox.substringAfter("/users/").substringBefore("/").toLong()))
            deliveredInboxes.add(inbox)
        }

        worker.deliverDue()

        assertEquals(List(10) { "https://same.example/users/$it/inbox" }, deliveredInboxes.toList())
    }

    @Test
    fun hostWithUnfinishedDeliveryIsNotClaimedAgain() = runBlocking {
        val releaseSlow = CompletableDeferred<Unit>()
        val otherDelivered = CompletableDeferred<Unit>()
        val queue = Queue(
            List(BATCH) { row(id = it.toLong(), inbox = "https://slow.example/inbox") } +
                List(BATCH) { row(id = 100L + it, inbox = "https://slow.example/inbox") } +
                row(id = 200, inbox = "https://other.example/inbox"),
        )
        val worker = worker(queue) { inbox ->
            if (inbox.startsWith("https://slow.example/")) releaseSlow.await() else otherDelivered.complete(Unit)
        }

        val running = launch { worker.run() }
        try {
            withTimeout(5000) { otherDelivered.await() }
            // 遅いホスト宛ての 2 回目の分は、1 回目を送り終えるまで取り出さない
            assertEquals(setOf("slow.example"), queue.excludedOnEachClaim[1])
        } finally {
            releaseSlow.complete(Unit)
            running.cancelAndJoin()
        }
    }

    private fun worker(queue: Queue, onDeliver: suspend (String) -> Unit) = DeliveryQueueWorker(
        queue,
        DeliverySenders { SENDER },
        object : ActivityDelivery {
            override suspend fun deliver(inbox: String, sender: LocalActor, body: ByteArray): DeliveryResult {
                onDeliver(inbox)
                return DeliveryResult.Delivered
            }
            override fun close() = Unit
        },
        DeliveryRetryPolicy(30.seconds, 24.hours, 7.days),
        Clock.fixed(NOW, ZoneOffset.UTC),
        OpenTelemetry.noop(),
    )

    private fun row(id: Long, inbox: String) = ClaimedDelivery(id, "alice", inbox, "body", 1, NOW, "follow")

    private class Queue(rows: List<ClaimedDelivery>) : DeliveryQueueRepository {
        private val pending = rows.toMutableList()
        val delivered: MutableList<Long> = Collections.synchronizedList(mutableListOf())

        val excludedOnEachClaim: MutableList<Set<String>> = Collections.synchronizedList(mutableListOf())

        @Synchronized
        override fun claim(now: Instant, limit: Int, excludedInboxHosts: Set<String>): List<ClaimedDelivery> {
            excludedOnEachClaim.add(excludedInboxHosts)
            val claimed = pending.filter { InboxHostLanes.hostOf(it.inbox) !in excludedInboxHosts }.take(limit)
            pending.removeAll(claimed)
            return claimed
        }
        override fun markDelivered(id: Long, deliveredAt: Instant) {
            delivered.add(id)
        }
        override fun scheduleRetry(id: Long, nextAttemptAt: Instant, error: String) = Unit
        override fun giveUp(id: Long, error: String, failedAt: Instant) = Unit
        override fun recoverDelivering(now: Instant): Int = 0
        override fun enqueueFollow(accountId: AccountId, inbox: String, body: String, now: Instant) = error("未使用")
        override fun enqueueQuoteAccept(accountId: AccountId, inbox: String, body: String, now: Instant) = error("未使用")
        override fun cancelPendingFollowAndEnqueueUndo(accountId: AccountId, inbox: String, followId: String, body: String, now: Instant) = error("未使用")
        override fun enqueueNote(post: NotePost): EnqueueNoteResult = error("未使用")
        override fun enqueueNoteDeletion(deletion: NoteDeletion): Boolean = error("未使用")
        override fun enqueueNoteUpdate(update: NoteUpdate): Boolean = error("未使用")
        override fun updateProfile(update: ProfileUpdate): Boolean = error("未使用")
    }

    private companion object {
        const val BATCH = 20
        val NOW: Instant = Instant.parse("2026-11-01T00:00:00Z")
        val SENDER = LocalActor(ActorUrls("local.example", "alice"), ActorPrivateKey(RsaKeys.generateKeyPair().private, ActorPrivateKey.Origin.Environment))
    }
}
