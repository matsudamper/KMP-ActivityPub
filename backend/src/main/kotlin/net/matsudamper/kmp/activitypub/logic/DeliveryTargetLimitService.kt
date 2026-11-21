package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.HostDeliveryInboxCount
import net.matsudamper.kmp.activitypub.repository.ServerSettingsRepository

/**
 * 同じホストからのフォローで配る先の上限。超える Follow は断る。
 * Mastodon などの普通のサーバーは sharedInbox で 1 か所にまとまるので、効いてくるのは sharedInbox を持たない実装だけ。
 * 正当なサーバーが上限に当たったときに、管理画面から引き上げられるよう DB に持つ
 */
class DeliveryTargetLimitService(
    private val settings: ServerSettingsRepository,
    private val followers: FollowerRepository,
) {
    fun maxPerHost(): Int = settings.findMaxDeliveryTargetsPerHost() ?: DEFAULT_MAX_PER_HOST

    /**
     * 配る先の数の多い順
     */
    fun hostCounts(): List<HostDeliveryInboxCount> = followers.countDeliveryInboxesByHost()

    fun updateMaxPerHost(limit: Int): UpdateDeliveryTargetLimitResult {
        if (limit !in MIN_MAX_PER_HOST..MAX_MAX_PER_HOST) return UpdateDeliveryTargetLimitResult.OutOfRange
        settings.saveMaxDeliveryTargetsPerHost(limit)
        return UpdateDeliveryTargetLimitResult.Updated
    }

    companion object {
        const val MIN_MAX_PER_HOST = 1

        /**
         * 投稿 1 件ごとに、この数の配信がホスト 1 つに飛ぶ
         */
        const val MAX_MAX_PER_HOST = 1000
        private const val DEFAULT_MAX_PER_HOST = 20
    }
}

sealed interface UpdateDeliveryTargetLimitResult {
    data object Updated : UpdateDeliveryTargetLimitResult

    data object OutOfRange : UpdateDeliveryTargetLimitResult
}
