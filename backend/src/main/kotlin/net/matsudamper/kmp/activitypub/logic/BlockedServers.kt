package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.TimelinePost

/**
 * 1 つのアカウントがブロックしているサーバー
 */
class BlockedServers(
    private val hosts: Set<String>,
) {
    /**
     * 投稿者かブーストした人がブロックしたサーバーのアクターなら true
     */
    fun hides(post: TimelinePost): Boolean = hidesActor(post.actorUri) || hidesActor(post.boostedBy?.actorUri)

    /**
     * @param actorUri このサーバーのアクターなら null
     */
    fun hidesActor(actorUri: String?): Boolean {
        if (hosts.isEmpty() || actorUri == null) return false
        return ServerHost.ofUri(actorUri) in hosts
    }
}
