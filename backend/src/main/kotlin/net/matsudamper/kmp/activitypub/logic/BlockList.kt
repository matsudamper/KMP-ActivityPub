package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.TimelinePost

/**
 * 1 つのアカウントがブロックしているサーバーとアクター
 *
 * @param hosts ブロックしているサーバーの [ServerHost]
 * @param actorUris ブロックしているアクター
 */
class BlockList(
    private val hosts: Set<String>,
    private val actorUris: Set<String>,
) {
    /**
     * 投稿者かブーストした人をブロックしていれば true
     */
    fun hides(post: TimelinePost): Boolean = hidesActor(post.actorUri) || hidesActor(post.boostedBy?.actorUri)

    /**
     * @param actorUri このサーバーのアクターなら null
     */
    fun hidesActor(actorUri: String?): Boolean {
        if (actorUri == null) return false
        if (actorUri in actorUris) return true
        return hosts.isNotEmpty() && ServerHost.ofUri(actorUri) in hosts
    }
}
