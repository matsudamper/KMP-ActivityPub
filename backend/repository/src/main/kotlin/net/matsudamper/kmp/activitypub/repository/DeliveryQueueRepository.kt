package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * 相手の inbox に送る配信の待ち行列。1 件が 1 宛先への 1 回の配信
 *
 * 状態は pending（送る時刻を待つ）/ delivering（送っている）/ failed（諦めた）。送れた行は消す。
 * 積むのは種別ごとの記録と同じトランザクションで書く側が行う（Accept なら [FollowerRepository.record]）
 */
interface DeliveryQueueRepository {
    /**
     * 投稿を記録して、宛先ごとに Create を積む。
     * 投稿の記録と投函を分けると、記録だけ残って誰にも届かない投稿ができる
     *
     * 宛先が無くても投稿は記録する。フォロワーがいない間の投稿も outbox には並ぶ
     *
     * 同じアカウントで同じ [NotePost.requestId] の投稿が既にあれば、何も書かずにその投稿を返す
     */
    fun enqueueNote(post: NotePost): EnqueueNoteResult

    /**
     * プロフィールを書き換えて、宛先ごとに Update を積む。
     * 書き換えと投函を分けると、こちらでは変わったのに相手には古いまま残る
     *
     * Update は前の分を丸ごと置き換えるので、まだ送っていない前の Update は消す。
     * 残すと、送り直しの順で古いプロフィールが後から届く
     *
     * @return アカウントが無ければ false
     */
    fun updateProfile(update: ProfileUpdate): Boolean

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

sealed interface EnqueueNoteResult {
    /**
     * @param deliveries 積んだ配信の数
     */
    data class Enqueued(
        val deliveries: Int,
    ) : EnqueueNoteResult

    data class AlreadyPosted(
        val publicId: PublicNoteId,
    ) : EnqueueNoteResult

    data object AccountNotFound : EnqueueNoteResult
}

/**
 * @param requestId 画面が送信ごとに作る ID。応答を受け取れずに送り直されたときに同じ投稿だと分かる
 * @param body 宛先すべてに同じものを送る Create のアクティビティ
 */
data class NotePost(
    val username: String,
    val requestId: String,
    val publicId: PublicNoteId,
    val contentHtml: String,
    val publishedAt: Instant,
    val body: String,
    val inboxes: List<String>,
)

/**
 * @param displayName 書き換えないなら [DisplayNameChange.Keep]
 * @param newIconPath 新しく置いたプロフィール画像の場所。null なら今の場所のまま。
 *   読んだ値を書き戻すと、間に別の更新が差し替えて消した画像を指してしまう
 * @param body 宛先すべてに同じものを送る Update のアクティビティ。プロフィールの全体を載せる
 */
data class ProfileUpdate(
    val username: String,
    val displayName: DisplayNameChange,
    val newIconPath: String?,
    val updatedAt: Instant,
    val body: String,
    val inboxes: List<String>,
)

/**
 * 読んだ値を書き戻さずに済むよう、書き換えないことを値で表す。書き戻すと間に入った別の更新を消す
 */
sealed interface DisplayNameChange {
    data object Keep : DisplayNameChange

    /**
     * @param value 未設定に戻すなら null
     */
    data class Set(
        val value: String?,
    ) : DisplayNameChange
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
