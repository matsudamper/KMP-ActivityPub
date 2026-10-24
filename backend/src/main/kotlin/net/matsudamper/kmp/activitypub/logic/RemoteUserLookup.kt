package net.matsudamper.kmp.activitypub.logic

/**
 * 他のサーバーのユーザーを acct から引く
 */
interface RemoteUserLookup {
    /**
     * 見つからない・相手のサーバーに届かないときは null
     */
    suspend fun find(
        username: String,
        host: String,
    ): RemoteUser?
}

/**
 * @param username 相手のサーバーでのユーザー名。acct のうちホストより前
 */
data class RemoteUser(
    val username: String,
    val displayName: String?,
)
