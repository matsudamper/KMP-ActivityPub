package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserBlockServerResult {
    /**
     * @param host 入力から取り出してブロックしたホスト
     */
    data class Blocked(
        val host: String,
    ) : UserBlockServerResult

    data class Failure(
        val message: String,
    ) : UserBlockServerResult
}
