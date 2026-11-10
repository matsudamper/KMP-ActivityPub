package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.CustomEmojiRecord

/**
 * 他のサーバーのユーザーを引く。見つからない・相手のサーバーに届かないときは null
 */
interface RemoteUserLookup {
    suspend fun findByAcct(
        username: String,
        host: String,
    ): RemoteUser?

    /**
     * @param url アクターの URL。プロフィールの URL でも、相手のサーバーがアクター文書を返すか、
     *   アクターへリダイレクトすれば引ける。他のホストへリダイレクトした場合はそのホストのユーザーになる
     */
    suspend fun findByUrl(url: String): RemoteUser?
}

/**
 * @param username 相手のサーバーでのユーザー名。acct のうちホストより前
 */
data class RemoteUser(
    val actorUri: String,
    val username: String,
    val displayName: String?,
    val iconUrl: String?,
    val displayNameEmojis: List<CustomEmojiRecord>,
)
