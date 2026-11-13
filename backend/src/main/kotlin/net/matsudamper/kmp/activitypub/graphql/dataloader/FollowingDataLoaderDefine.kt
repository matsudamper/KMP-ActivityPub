package net.matsudamper.kmp.activitypub.graphql.dataloader

import net.matsudamper.kmp.activitypub.graphql.otelSupplyAsync
import net.matsudamper.kmp.activitypub.logic.FollowingGroupService
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

/**
 * [Key.viewer] が [Key.actorUri] をフォローしているかと、フォローを入れた購読先
 */
class FollowingDataLoaderDefine(
    private val followingGroupService: FollowingGroupService,
) : DataLoaderDefine<FollowingDataLoaderDefine.Key, FollowingDataLoaderDefine.Following> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<Key, Following> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                keys.groupBy { it.viewer }
                    .flatMap { (viewer, viewerKeys) ->
                        val groupIdsByActorUri = followingGroupService.groupIdsAmong(viewer, viewerKeys.map { it.actorUri }.toSet())
                        viewerKeys.map { key ->
                            key to Following(
                                following = key.actorUri in groupIdsByActorUri,
                                groupId = groupIdsByActorUri[key.actorUri],
                            )
                        }
                    }.toMap()
            }
        }
    }

    data class Key(
        val viewer: AccountId,
        val actorUri: String,
    )

    /**
     * @param groupId フォローしていないか、メインに入っていれば null
     */
    data class Following(
        val following: Boolean,
        val groupId: FollowingGroupId?,
    )
}
