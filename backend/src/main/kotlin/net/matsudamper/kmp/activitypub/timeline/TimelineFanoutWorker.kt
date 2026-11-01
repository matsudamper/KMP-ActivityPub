package net.matsudamper.kmp.activitypub.timeline

import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.slf4j.LoggerFactory

class TimelineFanoutWorker(
    private val repository: TimelineRepository,
    private val store: TimelineStore,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(TimelineFanoutWorker::class.java)
    private val consumer = System.getenv("HOSTNAME") ?: UUID.randomUUID().toString()

    suspend fun run() {
        while (true) {
            try {
                withContext(Dispatchers.IO) { store.initializeGroup() }
                break
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                logger.warn("タイムライン配布のコンシューマーグループを作れなかった", failure)
                delay(1000)
            }
        }
        var block = true
        while (true) {
            try {
                val count = withContext(Dispatchers.IO) { runOnce(block) }
                block = count < 100
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                logger.warn("タイムライン配布に失敗した。未 ACK のジョブは再取得する", failure)
                delay(1000)
            }
        }
    }

    fun runOnce(block: Boolean): Int {
        val jobs = store.jobs(consumer, block)
        jobs.forEach { job ->
            distribute(job)
            store.acknowledge(job.id)
        }
        return jobs.size
    }

    private fun distribute(job: FanoutJob) {
        var after: AccountId? = null
        do {
            val recipients = repository.recipients(job.position.member, clock.instant().minusSeconds(604800), after, 1000)
            store.append(recipients, listOf(job.position))
            after = recipients.lastOrNull()
        } while (recipients.size == 1000)
    }
}
