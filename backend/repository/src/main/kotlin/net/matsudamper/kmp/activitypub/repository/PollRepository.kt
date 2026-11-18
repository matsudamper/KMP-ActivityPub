package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * 投稿に付いた投票。記録はこのサーバーの投稿なら [DeliveryQueueRepository.enqueueNote]、
 * 他のサーバーの投稿なら [TimelineRepository] が投稿と一緒に行う
 */
interface PollRepository {
    /**
     * このサーバーの投稿の投票と、届いた票から数えた票数。投票の無い投稿は入らない
     */
    fun localPolls(publicIds: Set<PublicNoteId>): Map<PublicNoteId, PollRecord>

    /**
     * 他のサーバーの投稿の投票と、届いた投稿に載っていた票数。投票の無い投稿と、保存していない投稿は入らない
     */
    fun remotePolls(objectUris: Set<String>): Map<String, PollRecord>

    /**
     * このサーバーの投稿の投票に届いた票を記録する
     *
     * @return 記録したら true。投票が無い、締め切りを過ぎた、選択肢に無い、1 つしか選べない投票に二度目の票、同じ票が二度届いた、のどれかなら false
     */
    fun recordVote(vote: PollVoteInput): Boolean
}

/**
 * @param options 並べた順
 * @param endsAt 締め切り。他のサーバーの投票で、相手のサーバーが付けていなければ null
 * @param votersCount 投票した人数。他のサーバーの投票で、相手のサーバーが出していなければ null
 */
data class PollRecord(
    val options: List<PollOptionRecord>,
    val multipleChoice: Boolean,
    val endsAt: Instant?,
    val votersCount: Long?,
)

/**
 * @param votesCount 他のサーバーの投票で、相手のサーバーが出していなければ null
 */
data class PollOptionRecord(
    val name: String,
    val votesCount: Long?,
)

/**
 * 投稿に付ける投票
 *
 * @param options 選択肢の文字列。並べた順
 */
data class NotePollInput(
    val options: List<String>,
    val multipleChoice: Boolean,
    val endsAt: Instant,
)

/**
 * @param voteUri 票の Note の id
 * @param choice 選んだ選択肢の文字列
 */
data class PollVoteInput(
    val publicId: PublicNoteId,
    val actorUri: String,
    val voteUri: String,
    val choice: String,
    val votedAt: Instant,
)
