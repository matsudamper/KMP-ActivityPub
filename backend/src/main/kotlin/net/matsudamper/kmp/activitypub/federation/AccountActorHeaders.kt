package net.matsudamper.kmp.activitypub.federation

import kotlin.time.Duration.Companion.minutes
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.ProfileHeaderPaths
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext
import net.matsudamper.kotpub.actor.ActorHeader
import net.matsudamper.kotpub.actor.ActorHeaders
import org.slf4j.LoggerFactory

internal class AccountActorHeaders(
    private val accountService: AccountService,
    private val imageStorage: ImageStorage,
) : ActorHeaders {
    private val logger = LoggerFactory.getLogger(AccountActorHeaders::class.java)

    override suspend fun find(username: String): ActorHeader? = withOpenTelemetryContext {
        val account = accountService.account(username) ?: return@withOpenTelemetryContext null
        val headerPath = account.headerPath?.let { ImagePath(it) } ?: return@withOpenTelemetryContext null

        when (val result = imageStorage.get(ImageStorage.GetRequest(ownerAccountId = account.id, path = headerPath))) {
            is ImageStorage.GetResult.Success -> ActorHeader(
                bytes = result.bytes,
                contentType = result.contentType,
                version = ProfileHeaderPaths.version(headerPath),
                cacheFor = UNVERSIONED_CACHE_DURATION,
            )

            ImageStorage.GetResult.NotFound -> null

            is ImageStorage.GetResult.Failure -> {
                logger.error("プロフィールヘッダーを取れなかった: ${headerPath.value}", result.cause)
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
