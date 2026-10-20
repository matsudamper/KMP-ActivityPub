package net.matsudamper.kmp.activitypub.repository

import java.time.Instant

/**
 * 相手の inbox に送る配信の待ち行列。1 件が 1 宛先への 1 回の配信
 *
 * 状態は pending（送る時刻を待つ）/ delivering（送っている）/ failed（諦めた）。送れた行は消す。
 * 積むのは種別ごとの記録と同じトランザクションで書く側が行う（Accept なら [FollowerRepository.record]）
 */
interface DeliveryQueueRepository {
    /**
     * 送る時刻を過ぎた pending を古い順に delivering にして返す。attempts はここで増やす
     */
    fun claim(
        now: Instant,
        limit: Int,
    ): List<ClaimedDelivery>

    /**
     * 送れたので行を消し、送れたことで確定する記録を一緒に書く。Accept ならフォローが成立する。
     * 分けると、書く前に落ちたときに送ったのに記録が無い状態が残り、行はもう無いので直せない
     *
     * 置き換えられて行が無ければ何もしない。新しい Follow への Accept はまだ送れていない
     */
    fun markDelivered(
        id: Long,
        deliveredAt: Instant,
    )

    fun scheduleRetry(
        id: Long,
        nextAttemptAt: Instant,
        error: String,
    )

    /**
     * 諦める。Accept なら成立していないフォローごと消す。
     * 置き換えられて行が無ければ何もしない。新しい Follow への Accept がまだ残っている
     */
    fun giveUp(
        id: Long,
        error: String,
    )

    /**
     * delivering のまま残っている行を pending に戻す。起動時に呼ぶ。
     * 相手に二重に届くことはあるが、受け取る側はアクティビティの id で重複を落とす
     *
     * @return 戻した件数
     */
    fun recoverDelivering(now: Instant): Int
}

/**
 * @param username 署名するこちらのアカウント
 * @param attempts この取り出しを含めて取り出された回数
 */
data class ClaimedDelivery(
    val id: Long,
    val username: String,
    val inbox: String,
    val body: String,
    val attempts: Int,
    val enqueuedAt: Instant,
)
