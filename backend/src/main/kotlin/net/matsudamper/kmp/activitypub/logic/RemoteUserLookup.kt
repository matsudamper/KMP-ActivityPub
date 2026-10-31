package net.matsudamper.kmp.activitypub.logic

/**
 * 他のサーバーのユーザーを引く。見つからない・相手のサーバーに届かないときは null
 */
interface RemoteUserLookup {
    suspend fun findByAcct(
        username: String,
        host: String,
    ): RemoteUser?

    /**
     * @param url アクターの URL。プロフィールの URL でも、相手のサーバーがアクター文書を返せば引ける
     */
    suspend fun findByUrl(url: String): RemoteUser?
}

/**
 * @param username 相手のサーバーでのユーザー名。acct のうちホストより前
 */
data class RemoteUser(
    val username: String,
    val displayName: String?,
    val iconUrl: String?,
)
