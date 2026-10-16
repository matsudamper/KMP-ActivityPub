package net.matsudamper.kmp.activitypub.frontend.logic.admin

/**
 * @param acct Mastodon の検索窓に貼る形
 * @param createdAt 登録した時刻。エポックからの秒数
 */
data class AdminAccount(
    val username: String,
    val acct: String,
    val createdAt: Long,
)
