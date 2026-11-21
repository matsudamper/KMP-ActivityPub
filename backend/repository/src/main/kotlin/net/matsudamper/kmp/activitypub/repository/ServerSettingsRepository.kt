package net.matsudamper.kmp.activitypub.repository

/**
 * 管理画面で変えるサーバー全体の設定。既定の値はここでは持たない
 */
interface ServerSettingsRepository {
    /**
     * @return 管理者が変えていなければ null
     */
    fun findMaxDeliveryTargetsPerHost(): Int?

    fun saveMaxDeliveryTargetsPerHost(limit: Int)
}
