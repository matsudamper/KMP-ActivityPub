package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.util.UUID
import net.matsudamper.kmp.activitypub.delivery.DeliverySenders
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.DisplayNameChange
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.ProfileUpdate
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kotpub.actor.ActorAppearance
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
 * @param imageStorage null なら画像を置けないので、プロフィール画像は変えられない
 * @param onEnqueued 積んだ後に呼ぶ。次の巡回を待たずに送らせる
 */
class ProfileUpdateService(
    private val senders: DeliverySenders,
    private val accountService: AccountService,
    private val appearances: StoredActorAppearances,
    private val profiles: StoredActorProfiles,
    private val imageStorage: ImageStorage?,
    private val followerRepository: FollowerRepository,
    private val deliveryQueueRepository: DeliveryQueueRepository,
    private val clock: Clock,
    private val onEnqueued: () -> Unit,
) {
    private val logger = LoggerFactory.getLogger(ProfileUpdateService::class.java)

    /**
     * @param displayName null なら今の表示名のまま。空なら未設定に戻す
     * @param icon 新しいプロフィール画像。null なら今の画像のまま
     */
    fun update(
        username: String,
        displayName: String?,
        icon: ByteArray?,
    ): UpdateProfileResult {
        val displayNameChange =
            if (displayName == null) {
                DisplayNameChange.Keep
            } else {
                val newDisplayName = displayName.trim().ifEmpty { null }
                // 書いた人の数え方と合わせるため、コードポイントで数える
                if (newDisplayName != null && newDisplayName.codePointCount(0, newDisplayName.length) > MAX_DISPLAY_NAME_LENGTH) {
                    return UpdateProfileResult.TooLong
                }
                DisplayNameChange.Set(newDisplayName)
            }
        val iconFormat =
            if (icon == null) {
                null
            } else {
                if (icon.size > MAX_ICON_BYTES) return UpdateProfileResult.IconTooLarge
                val format = ProfileIconFormat.detect(icon) ?: return UpdateProfileResult.IconUnsupported
                val dimensions = ProfileIconDimensions.read(icon, format) ?: return UpdateProfileResult.IconUnsupported
                if (dimensions.width > MAX_ICON_SIDE_PX || dimensions.height > MAX_ICON_SIDE_PX) {
                    return UpdateProfileResult.IconDimensionsTooLarge
                }
                format
            }
        val sender = senders.find(username) ?: return UpdateProfileResult.AccountNotFound
        val account = accountService.account(sender.urls.username) ?: return UpdateProfileResult.AccountNotFound

        val newIconPath =
            if (icon == null || iconFormat == null) {
                null
            } else {
                when (val result = putIcon(accountId = account.id, icon = icon, format = iconFormat)) {
                    is PutIconResult.Success -> result.path
                    is PutIconResult.Failure -> return result.result
                }
            }

        // 保存より前に組み立てるので、変えない項目は保存済みの値を、変える項目はこれから書く値を載せる
        val storedProfile = profiles.find(sender.urls.username)
        val updatedProfile =
            when (displayNameChange) {
                DisplayNameChange.Keep -> storedProfile
                is DisplayNameChange.Set -> storedProfile.copy(displayName = displayNameChange.value)
            }
        val storedAppearance = appearances.find(sender.urls.username)
        val updatedAppearance =
            if (newIconPath == null) {
                storedAppearance
            } else {
                storedAppearance.copy(iconVersion = ProfileIconPaths.version(newIconPath))
            }
        val body =
            ActorPublisher(
                appearances = object : StoredActorAppearances {
                    override fun find(username: String): ActorAppearance = updatedAppearance
                },
                profiles = object : StoredActorProfiles {
                    override fun find(username: String): ActorProfile = updatedProfile
                },
                webPages = null,
            ).prepareUpdate(sender)
        val updated =
            deliveryQueueRepository.updateProfile(
                ProfileUpdate(
                    username = sender.urls.username,
                    displayName = displayNameChange,
                    newIconPath = newIconPath?.value,
                    updatedAt = clock.instant(),
                    body = body,
                    inboxes = followerRepository.findDeliveryInboxes(sender.urls.username),
                ),
            )
        if (!updated) {
            if (newIconPath != null) deleteIcon(accountId = account.id, path = newIconPath)
            return UpdateProfileResult.AccountNotFound
        }
        val replacedIconPath = account.iconPath
        if (newIconPath != null && replacedIconPath != null) {
            deleteIcon(accountId = account.id, path = ImagePath(replacedIconPath))
        }

        logger.info("プロフィールの更新を積んだ: ${sender.urls.username}")
        onEnqueued()
        return UpdateProfileResult.Updated
    }

    private fun putIcon(
        accountId: AccountId,
        icon: ByteArray,
        format: ProfileIconFormat,
    ): PutIconResult {
        val storage = imageStorage ?: return PutIconResult.Failure(UpdateProfileResult.ImageStorageDisabled)
        val result =
            storage.put(
                ImageStorage.PutRequest(
                    ownerAccountId = accountId,
                    path = ProfileIconPaths.create(accountId),
                    contentType = format.contentType,
                    maxBytes = MAX_ICON_BYTES.toLong(),
                    inputStream = icon.inputStream(),
                ),
            )
        return when (result) {
            is ImageStorage.PutResult.Success -> PutIconResult.Success(result.path)

            ImageStorage.PutResult.PayloadTooLarge -> PutIconResult.Failure(UpdateProfileResult.IconTooLarge)

            ImageStorage.PutResult.Empty -> PutIconResult.Failure(UpdateProfileResult.IconUnsupported)

            is ImageStorage.PutResult.Failure -> {
                logger.error("プロフィール画像を置けなかった: $accountId", result.cause)
                PutIconResult.Failure(UpdateProfileResult.IconUploadFailed)
            }
        }
    }

    /**
     * 消せなくても使われない画像が残るだけなので、更新は失敗にしない
     */
    private fun deleteIcon(
        accountId: AccountId,
        path: ImagePath,
    ) {
        val storage = imageStorage ?: return
        val result = storage.delete(ImageStorage.DeleteRequest(ownerAccountId = accountId, path = path))
        if (result is ImageStorage.DeleteResult.Failure) {
            logger.warn("使わなくなったプロフィール画像を消せなかった: ${path.value}", result.cause)
        }
    }

    private sealed interface PutIconResult {
        data class Success(val path: ImagePath) : PutIconResult

        data class Failure(val result: UpdateProfileResult) : PutIconResult
    }

    companion object {
        /**
         * Mastodon の表示名の上限
         */
        const val MAX_DISPLAY_NAME_LENGTH: Int = 30

        /**
         * Mastodon のプロフィール画像の上限
         */
        const val MAX_ICON_BYTES: Int = 2 * 1024 * 1024

        /**
         * 縦横それぞれの上限。画面は送る前にこの大きさに縮める。Mastodon のプロフィール画像と同じ
         */
        const val MAX_ICON_SIDE_PX: Int = 400
    }
}

/**
 * 画像の置き場の中でのプロフィール画像の場所
 */
internal object ProfileIconPaths {
    /**
     * 差し替えるたびに別の場所に置く。相手は画像を URL で覚えるので、同じ場所に上書きすると前の画像が出続ける。
     * 置き場の権限をアカウント単位に絞れるよう、`users/{アカウントの id}/` の下に置く
     */
    fun create(accountId: AccountId): ImagePath = ImagePath("users/${accountId.value}/profile-icon/${UUID.randomUUID()}")

    /**
     * [ActorAppearance.iconVersion] に使う値。場所ごとに違う
     */
    fun version(path: ImagePath): String = path.value.substringAfterLast('/')
}

sealed interface UpdateProfileResult {
    data object Updated : UpdateProfileResult

    data object TooLong : UpdateProfileResult

    data object IconTooLarge : UpdateProfileResult

    /**
     * 縦か横が [ProfileUpdateService.MAX_ICON_SIDE_PX] を超えている
     */
    data object IconDimensionsTooLarge : UpdateProfileResult

    /**
     * PNG / JPEG / GIF / WebP ではない
     */
    data object IconUnsupported : UpdateProfileResult

    data object ImageStorageDisabled : UpdateProfileResult

    data object IconUploadFailed : UpdateProfileResult

    /**
     * アカウントが無いか、鍵が無くてアクターとして扱えない
     */
    data object AccountNotFound : UpdateProfileResult
}
