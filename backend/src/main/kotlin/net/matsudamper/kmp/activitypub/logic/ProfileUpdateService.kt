package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.net.URISyntaxException
import java.time.Clock
import java.util.UUID
import net.matsudamper.kmp.activitypub.delivery.DeliverySenders
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.ProfileLink
import net.matsudamper.kmp.activitypub.repository.ProfileUpdate
import net.matsudamper.kmp.activitypub.repository.ProfileValueChange
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kotpub.actor.ActorAppearance
import net.matsudamper.kotpub.actor.ActorLink
import net.matsudamper.kotpub.actor.ActorProfile
import net.matsudamper.kotpub.actor.ActorPublisher
import net.matsudamper.kotpub.actor.LocalActor
import net.matsudamper.kotpub.actor.StoredActorAppearances
import net.matsudamper.kotpub.actor.StoredActorProfiles
import net.matsudamper.kotpub.url.WebPageUrls
import org.slf4j.LoggerFactory

/**
 * プロフィールを書き換えて、フォロワーへの Update を積む。送るのは配信キューのワーカー
 *
 * @param appearances Actor エンドポイントと同じもの。Update には Actor の文書を丸ごと載せる
 * @param profiles Actor エンドポイントと同じもの
 * @param webPages Actor エンドポイントと同じもの
 * @param imageStorage null なら画像を置けないので、プロフィール画像は変えられない
 * @param onEnqueued 積んだ後に呼ぶ。次の巡回を待たずに送らせる
 */
class ProfileUpdateService(
    private val senders: DeliverySenders,
    private val accountService: AccountService,
    private val appearances: StoredActorAppearances,
    private val profiles: StoredActorProfiles,
    private val webPages: WebPageUrls,
    private val imageStorage: ImageStorage?,
    private val followerRepository: FollowerRepository,
    private val deliveryQueueRepository: DeliveryQueueRepository,
    private val clock: Clock,
    private val onEnqueued: () -> Unit,
) {
    private val logger = LoggerFactory.getLogger(ProfileUpdateService::class.java)

    /**
     * null の項目は今のまま
     *
     * @param displayName 空なら未設定に戻す
     * @param summary 空なら未設定に戻す
     * @param links 名前も URL も空の行は捨てる。空のリストならリンク集を消す
     */
    fun updateProfile(
        username: String,
        displayName: String?,
        summary: String?,
        links: List<ProfileLink>?,
    ): UpdateProfileResult {
        val displayNameChange =
            when (val result = displayNameChange(displayName)) {
                is ValidationResult.Valid -> result.change
                is ValidationResult.Invalid -> return result.result
            }
        val summaryChange =
            when (val result = summaryChange(summary)) {
                is ValidationResult.Valid -> result.change
                is ValidationResult.Invalid -> return result.result
            }
        val linksChange =
            when (val result = linksChange(links)) {
                is ValidationResult.Valid -> result.change
                is ValidationResult.Invalid -> return result.result
            }
        val target = findTarget(username) ?: return UpdateProfileResult.AccountNotFound

        val enqueued =
            enqueueUpdate(
                target = target,
                changes = ProfileChanges(displayName = displayNameChange, summary = summaryChange, links = linksChange),
                newIconPath = null,
            )
        return if (enqueued) UpdateProfileResult.Updated else UpdateProfileResult.AccountNotFound
    }

    /**
     * 差し替えたら前の画像を消す
     */
    fun updateIcon(
        username: String,
        icon: ByteArray,
    ): UpdateIconResult {
        if (icon.size > MAX_ICON_BYTES) return UpdateIconResult.TooLarge
        val format = ImageFormat.detect(icon) ?: return UpdateIconResult.Unsupported
        val dimensions = ProfileIconDimensions.read(icon, format) ?: return UpdateIconResult.Unsupported
        if (dimensions.width > MAX_ICON_SIDE_PX || dimensions.height > MAX_ICON_SIDE_PX) {
            return UpdateIconResult.DimensionsTooLarge
        }
        val target = findTarget(username) ?: return UpdateIconResult.AccountNotFound
        val accountId = target.account.id

        val newIconPath =
            when (val result = putIcon(accountId = accountId, icon = icon, format = format)) {
                is PutIconResult.Success -> result.path
                is PutIconResult.Failure -> return result.result
            }
        val enqueued = enqueueUpdate(target = target, changes = ProfileChanges.KEEP_ALL, newIconPath = newIconPath)
        if (!enqueued) {
            deleteIcon(accountId = accountId, path = newIconPath)
            return UpdateIconResult.AccountNotFound
        }
        val replacedIconPath = target.account.iconPath
        if (replacedIconPath != null) {
            deleteIcon(accountId = accountId, path = ImagePath(replacedIconPath))
        }
        return UpdateIconResult.Updated
    }

    private fun displayNameChange(displayName: String?): ValidationResult<String?> {
        if (displayName == null) return ValidationResult.Valid(ProfileValueChange.Keep)
        val newDisplayName = displayName.trim().ifEmpty { null }
        if (newDisplayName != null && newDisplayName.codePointLength() > MAX_DISPLAY_NAME_LENGTH) {
            return ValidationResult.Invalid(UpdateProfileResult.DisplayNameTooLong)
        }
        return ValidationResult.Valid(ProfileValueChange.Set(newDisplayName))
    }

    private fun summaryChange(summary: String?): ValidationResult<String?> {
        if (summary == null) return ValidationResult.Valid(ProfileValueChange.Keep)
        val newSummary = summary.replace("\r\n", "\n").trim().ifEmpty { null }
        if (newSummary != null && newSummary.codePointLength() > MAX_SUMMARY_LENGTH) {
            return ValidationResult.Invalid(UpdateProfileResult.SummaryTooLong)
        }
        return ValidationResult.Valid(ProfileValueChange.Set(newSummary))
    }

    private fun linksChange(links: List<ProfileLink>?): ValidationResult<List<ProfileLink>> {
        if (links == null) return ValidationResult.Valid(ProfileValueChange.Keep)
        val newLinks =
            links
                .map { ProfileLink(name = it.name.trim(), url = it.url.trim()) }
                .filterNot { it.name.isEmpty() && it.url.isEmpty() }
        if (newLinks.size > MAX_LINK_COUNT) return ValidationResult.Invalid(UpdateProfileResult.TooManyLinks)
        if (newLinks.any { it.name.codePointLength() > MAX_LINK_PART_LENGTH || it.url.codePointLength() > MAX_LINK_PART_LENGTH }) {
            return ValidationResult.Invalid(UpdateProfileResult.LinkTooLong)
        }
        if (newLinks.any { it.name.isEmpty() || isWebUrl(it.url).not() }) {
            return ValidationResult.Invalid(UpdateProfileResult.InvalidLink)
        }
        return ValidationResult.Valid(ProfileValueChange.Set(newLinks))
    }

    /**
     * kotpub は http と https 以外のリンクを Actor に載せずに捨てるので、保存する前に断る
     */
    private fun isWebUrl(url: String): Boolean {
        val uri =
            try {
                URI(url)
            } catch (_: URISyntaxException) {
                return false
            }
        val scheme = uri.scheme?.lowercase()
        return (scheme == "http" || scheme == "https") && uri.host.isNullOrEmpty().not()
    }

    // 書いた人の数え方と合わせるため、コードポイントで数える
    private fun String.codePointLength(): Int = codePointCount(0, length)

    private fun findTarget(username: String): UpdateTarget? {
        val sender = senders.find(username) ?: return null
        val account = accountService.account(sender.urls.username) ?: return null
        return UpdateTarget(sender = sender, account = account)
    }

    /**
     * @param newIconPath 新しく置いたプロフィール画像。null なら今の画像のまま
     * @return アカウントが無くて積めなければ false
     */
    private fun enqueueUpdate(
        target: UpdateTarget,
        changes: ProfileChanges,
        newIconPath: ImagePath?,
    ): Boolean {
        val username = target.sender.urls.username
        // 保存より前に組み立てるので、変えない項目は保存済みの値を、変える項目はこれから書く値を載せる
        val storedProfile = profiles.find(username)
        val updatedProfile =
            storedProfile.copy(
                displayName = changes.displayName.applyTo(storedProfile.displayName),
                summary = changes.summary.applyTo(storedProfile.summary),
            )
        val storedAppearance = appearances.find(username)
        val updatedAppearance =
            storedAppearance.copy(
                links =
                when (val links = changes.links) {
                    ProfileValueChange.Keep -> storedAppearance.links
                    is ProfileValueChange.Set -> links.value.map { ActorLink(name = it.name, url = it.url) }
                },
                iconVersion = newIconPath?.let { ProfileIconPaths.version(it) } ?: storedAppearance.iconVersion,
            )
        val body =
            ActorPublisher(
                appearances = object : StoredActorAppearances {
                    override fun find(username: String): ActorAppearance = updatedAppearance
                },
                profiles = object : StoredActorProfiles {
                    override fun find(username: String): ActorProfile = updatedProfile
                },
                webPages = webPages,
            ).prepareUpdate(target.sender)
        val updated =
            deliveryQueueRepository.updateProfile(
                ProfileUpdate(
                    username = username,
                    displayName = changes.displayName,
                    summary = changes.summary,
                    links = changes.links,
                    newIconPath = newIconPath?.value,
                    updatedAt = clock.instant(),
                    body = body,
                    inboxes = followerRepository.findDeliveryInboxes(username),
                ),
            )
        if (!updated) return false

        logger.info("プロフィールの更新を積んだ: $username")
        onEnqueued()
        return true
    }

    private fun putIcon(
        accountId: AccountId,
        icon: ByteArray,
        format: ImageFormat,
    ): PutIconResult {
        val storage = imageStorage ?: return PutIconResult.Failure(UpdateIconResult.ImageStorageDisabled)
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

            ImageStorage.PutResult.PayloadTooLarge -> PutIconResult.Failure(UpdateIconResult.TooLarge)

            ImageStorage.PutResult.Empty -> PutIconResult.Failure(UpdateIconResult.Unsupported)

            is ImageStorage.PutResult.Failure -> {
                logger.error("プロフィール画像を置けなかった: $accountId", result.cause)
                PutIconResult.Failure(UpdateIconResult.UploadFailed)
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

    private fun <T> ProfileValueChange<T>.applyTo(stored: T): T =
        when (this) {
            ProfileValueChange.Keep -> stored
            is ProfileValueChange.Set -> value
        }

    private class UpdateTarget(
        val sender: LocalActor,
        val account: Account,
    )

    private class ProfileChanges(
        val displayName: ProfileValueChange<String?>,
        val summary: ProfileValueChange<String?>,
        val links: ProfileValueChange<List<ProfileLink>>,
    ) {
        companion object {
            val KEEP_ALL: ProfileChanges =
                ProfileChanges(displayName = ProfileValueChange.Keep, summary = ProfileValueChange.Keep, links = ProfileValueChange.Keep)
        }
    }

    private sealed interface ValidationResult<out T> {
        data class Valid<T>(val change: ProfileValueChange<T>) : ValidationResult<T>

        data class Invalid(val result: UpdateProfileResult) : ValidationResult<Nothing>
    }

    private sealed interface PutIconResult {
        data class Success(val path: ImagePath) : PutIconResult

        data class Failure(val result: UpdateIconResult) : PutIconResult
    }

    companion object {
        /**
         * Mastodon の表示名の上限
         */
        const val MAX_DISPLAY_NAME_LENGTH: Int = 30

        /**
         * Mastodon のプロフィールの説明文の上限
         */
        const val MAX_SUMMARY_LENGTH: Int = 500

        /**
         * Mastodon のプロフィールの補足情報の数の上限
         */
        const val MAX_LINK_COUNT: Int = 4

        /**
         * Mastodon のプロフィールの補足情報の、見出しと内容それぞれの上限
         */
        const val MAX_LINK_PART_LENGTH: Int = 255

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

    data object DisplayNameTooLong : UpdateProfileResult

    data object SummaryTooLong : UpdateProfileResult

    data object TooManyLinks : UpdateProfileResult

    /**
     * リンクの見出しか URL が [ProfileUpdateService.MAX_LINK_PART_LENGTH] を超えている
     */
    data object LinkTooLong : UpdateProfileResult

    /**
     * 見出しが空か、URL が http / https ではない
     */
    data object InvalidLink : UpdateProfileResult

    /**
     * アカウントが無いか、鍵が無くてアクターとして扱えない
     */
    data object AccountNotFound : UpdateProfileResult
}

sealed interface UpdateIconResult {
    data object Updated : UpdateIconResult

    data object TooLarge : UpdateIconResult

    /**
     * 縦か横が [ProfileUpdateService.MAX_ICON_SIDE_PX] を超えている
     */
    data object DimensionsTooLarge : UpdateIconResult

    /**
     * PNG / JPEG / GIF / WebP ではないか、寸法を読めない
     */
    data object Unsupported : UpdateIconResult

    data object ImageStorageDisabled : UpdateIconResult

    data object UploadFailed : UpdateIconResult

    /**
     * アカウントが無いか、鍵が無くてアクターとして扱えない
     */
    data object AccountNotFound : UpdateIconResult
}
