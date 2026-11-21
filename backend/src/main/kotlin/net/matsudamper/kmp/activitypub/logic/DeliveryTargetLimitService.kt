package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.HostDeliveryInboxCount

/**
 * @param maxPerHost 同じホストからのフォローで配る先の上限。超える Follow は断る
 */
class DeliveryTargetLimitService(
    val maxPerHost: Int,
    private val followers: FollowerRepository,
) {
    /**
     * 配る先の数の多い順
     */
    fun hostCounts(): List<HostDeliveryInboxCount> = followers.countDeliveryInboxesByHost()
}
