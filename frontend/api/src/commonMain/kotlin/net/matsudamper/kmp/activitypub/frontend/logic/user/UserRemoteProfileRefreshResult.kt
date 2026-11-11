package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserRemoteProfileRefreshResult {
    /**
     * @param progress 一度も始めていなければ null
     */
    data class Success(
        val progress: Progress?,
    ) : UserRemoteProfileRefreshResult

    data class Failure(
        val message: String,
    ) : UserRemoteProfileRefreshResult

    /**
     * @param failed 取りに行ったが取れなかった数
     * @param skipped 相手のサーバーが続けて失敗したので取りに行かなかった数
     * @param finishedAt 終わっていなければ null
     */
    data class Progress(
        val running: Boolean,
        val total: Int,
        val refreshed: Int,
        val failed: Int,
        val skipped: Int,
        val startedAt: String,
        val finishedAt: String?,
    )
}
