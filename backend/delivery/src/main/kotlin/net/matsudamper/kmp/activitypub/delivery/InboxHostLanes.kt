package net.matsudamper.kmp.activitypub.delivery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.repository.ClaimedDelivery
import net.matsudamper.kmp.activitypub.repository.InboxHost

/**
 * inbox のホストごとに 1 本ずつの、送る順番の列。
 *
 * 列には取り出した順のまま入れ、ホストごとに 1 本の送り手が先頭から 1 件ずつ送る。
 * 送り手を行ごとに起こすと、Follow より後に積んだ Undo が先に届くように順番が入れ替わる。
 * 列が空になったら列ごと消すので、送ったことのあるホストの数だけ溜まり続けない
 *
 * @param onLaneClosed 列が空になって消えたときに呼ぶ。そのホスト宛ての行をまた取り出せるようになる
 */
internal class InboxHostLanes(
    private val onLaneClosed: () -> Unit,
) {
    private val lanes = mutableMapOf<String, ArrayDeque<ClaimedDelivery>>()

    /**
     * 列があるホスト。送り終えていない行があるので、これらのホスト宛ては新しく取り出さない
     */
    val activeHosts: Set<String> get() = synchronized(lanes) { lanes.keys.toSet() }

    val activeCount: Int get() = synchronized(lanes) { lanes.size }

    fun enqueue(
        scope: CoroutineScope,
        rows: List<ClaimedDelivery>,
        send: suspend (ClaimedDelivery) -> Unit,
    ) {
        val openedLanes = synchronized(lanes) {
            rows.mapNotNull { row ->
                val host = InboxHost.of(row.inbox)
                val lane = lanes[host]
                if (lane == null) {
                    val openedLane = ArrayDeque(listOf(row))
                    lanes[host] = openedLane
                    host to openedLane
                } else {
                    lane.addLast(row)
                    null
                }
            }
        }
        openedLanes.forEach { (host, lane) -> scope.launch { drain(host, lane, send) } }
    }

    private suspend fun drain(
        host: String,
        lane: ArrayDeque<ClaimedDelivery>,
        send: suspend (ClaimedDelivery) -> Unit,
    ) {
        try {
            while (true) {
                // 空なのを見てから消すまでの間に足された行を取りこぼさないよう、同じロックの中で消す
                val row = synchronized(lanes) {
                    val next = lane.removeFirstOrNull()
                    if (next == null) lanes.remove(host)
                    next
                }
                if (row == null) break
                send(row)
            }
        } finally {
            synchronized(lanes) {
                if (lanes[host] === lane) lanes.remove(host)
            }
            onLaneClosed()
        }
    }
}
