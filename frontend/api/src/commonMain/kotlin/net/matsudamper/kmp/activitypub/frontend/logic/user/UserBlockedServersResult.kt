package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserBlockedServersResult {
    /**
     * @param hosts 新しくブロックした順
     */
    data class Success(
        val hosts: List<String>,
    ) : UserBlockedServersResult

    data class Failure(
        val message: String,
    ) : UserBlockedServersResult
}
