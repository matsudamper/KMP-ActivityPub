package net.matsudamper.kmp.activitypub.frontend.logic.admin

/**
 * @param acct Mastodon の検索窓に貼る形。サーバーのドメインが未設定なら null
 */
data class AdminAccount(
    val username: String,
    val acct: String?,
)
