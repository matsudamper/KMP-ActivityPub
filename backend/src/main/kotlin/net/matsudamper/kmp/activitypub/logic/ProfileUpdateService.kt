package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import net.matsudamper.kmp.activitypub.delivery.DeliverySenders
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.ProfileUpdate
import net.matsudamper.kotpub.actor.ActorProfile
import net.matsudamper.kotpub.actor.ActorPublisher
import net.matsudamper.kotpub.actor.StoredActorAppearances
import net.matsudamper.kotpub.actor.StoredActorProfiles
import org.slf4j.LoggerFactory

/**
 * プロフィールを書き換えて、フォロワーへの Update を積む。送るのは配信キューのワーカー
 *
 * @param appearances Actor エンドポイントと同じもの。Update には Actor の文書を丸ごと載せる
 * @param profiles Actor エンドポイントと同じもの
 * @param onEnqueued 積んだ後に呼ぶ。次の巡回を待たずに送らせる
 */
class ProfileUpdateService(
    private val senders: DeliverySenders,
    private val appearances: StoredActorAppearances,
    private val profiles: StoredActorProfiles,
    private val followerRepository: FollowerRepository,
    private val deliveryQueueRepository: DeliveryQueueRepository,
    private val clock: Clock,
    private val onEnqueued: () -> Unit,
) {
    private val logger = LoggerFactory.getLogger(ProfileUpdateService::class.java)

    /**
     * @param displayName 空なら未設定に戻す
     */
    fun update(
        username: String,
        displayName: String,
    ): UpdateProfileResult {
        val newDisplayName = displayName.trim().ifEmpty { null }
        // 書いた人の数え方と合わせるため、コードポイントで数える
        if (newDisplayName != null && newDisplayName.codePointCount(0, newDisplayName.length) > MAX_DISPLAY_NAME_LENGTH) {
            return UpdateProfileResult.TooLong
        }
        val sender = senders.find(username) ?: return UpdateProfileResult.AccountNotFound

        // 保存より前に組み立てるので、変えない項目は保存済みの値を、変える項目はこれから書く値を載せる
        val updatedProfile = profiles.find(sender.urls.username).copy(displayName = newDisplayName)
        val body =
            ActorPublisher(
                appearances = appearances,
                profiles = object : StoredActorProfiles {
                    override fun find(username: String): ActorProfile = updatedProfile
                },
                webPages = null,
            ).prepareUpdate(sender)
        val updated =
            deliveryQueueRepository.updateProfile(
                ProfileUpdate(
                    username = sender.urls.username,
                    displayName = newDisplayName,
                    updatedAt = clock.instant(),
                    body = body,
                    inboxes = followerRepository.findDeliveryInboxes(sender.urls.username),
                ),
            )
        if (!updated) return UpdateProfileResult.AccountNotFound

        logger.info("プロフィールの更新を積んだ: ${sender.urls.username}")
        onEnqueued()
        return UpdateProfileResult.Updated
    }

    companion object {
        /**
         * Mastodon の表示名の上限
         */
        const val MAX_DISPLAY_NAME_LENGTH: Int = 30
    }
}

sealed interface UpdateProfileResult {
    data object Updated : UpdateProfileResult

    data object TooLong : UpdateProfileResult

    /**
     * アカウントが無いか、鍵が無くてアクターとして扱えない
     */
    data object AccountNotFound : UpdateProfileResult
}
