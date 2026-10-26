package net.matsudamper.kmp.activitypub.federation

import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.ProfileIconPaths
import net.matsudamper.kotpub.actor.ActorIcon
import net.matsudamper.kotpub.actor.ActorIcons
import org.slf4j.LoggerFactory

internal class AccountActorIcons(
    private val accountService: AccountService,
    private val imageStorage: ImageStorage,
) : ActorIcons {
    private val logger = LoggerFactory.getLogger(AccountActorIcons::class.java)

    override suspend fun find(username: String): ActorIcon? = withContext(Dispatchers.IO) {
        val account = accountService.account(username) ?: return@withContext null
        val iconPath = account.iconPath?.let { ImagePath(it) } ?: return@withContext null

        when (val result = imageStorage.get(ImageStorage.GetRequest(ownerAccountId = account.id, path = iconPath))) {
            is ImageStorage.GetResult.Success -> ActorIcon(
                bytes = result.bytes,
                contentType = result.contentType,
                version = ProfileIconPaths.version(iconPath),
                cacheFor = UNVERSIONED_CACHE_DURATION,
            )

            ImageStorage.GetResult.NotFound -> null

            is ImageStorage.GetResult.Failure -> {
                logger.error("プロフィール画像を取れなかった: ${iconPath.value}", result.cause)
                null
            }
        }
    }

    private companion object {
        /**
         * バージョンの付いていない URL は差し替えで中身が変わるので短くする
         */
        val UNVERSIONED_CACHE_DURATION = 10.minutes
    }
}
