package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * @param url 相手のサーバーにある動画の URL。中継していない
 * @param description 動画の説明文。無ければ null
 */
data class UserPostVideo(
    val url: String,
    val description: String?,
)
