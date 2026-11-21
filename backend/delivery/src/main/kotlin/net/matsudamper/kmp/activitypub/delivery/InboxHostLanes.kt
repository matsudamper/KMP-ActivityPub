package net.matsudamper.kmp.activitypub.delivery

import java.net.URI
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * inbox のホストごとに 1 本ずつの列。同じホスト宛ては前の配信が終わるまで待たせる
 *
 * 使い終わったホストの列は消すので、送ったことのあるホストの数だけ溜まり続けない
 */
internal class InboxHostLanes {
    private val lanes = mutableMapOf<String, Lane>()

    suspend fun <T> withLane(host: String, block: suspend () -> T): T {
        val lane = synchronized(lanes) {
            lanes.getOrPut(host) { Lane() }.also { it.users++ }
        }
        try {
            return lane.mutex.withLock { block() }
        } finally {
            synchronized(lanes) {
                lane.users--
                if (lane.users == 0) lanes.remove(host)
            }
        }
    }

    private class Lane {
        val mutex = Mutex()
        var users = 0
    }

    companion object {
        /**
         * URL として読めない inbox は、文字列そのものを 1 つのホストとして扱う
         */
        fun hostOf(inbox: String): String =
            runCatching { URI(inbox).host }.getOrNull()?.lowercase() ?: inbox
    }
}
