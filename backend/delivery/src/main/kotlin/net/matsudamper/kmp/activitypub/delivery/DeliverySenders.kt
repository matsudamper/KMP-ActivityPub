package net.matsudamper.kmp.activitypub.delivery

import net.matsudamper.kotpub.actor.LocalActor

/**
 * 配信に署名するこちらのアカウントを引く
 */
fun interface DeliverySenders {
    /**
     * @return アカウントか鍵が無ければ null
     */
    fun find(username: String): LocalActor?
}
