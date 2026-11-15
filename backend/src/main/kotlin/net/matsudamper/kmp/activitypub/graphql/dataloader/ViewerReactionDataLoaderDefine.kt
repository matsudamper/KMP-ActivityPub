package net.matsudamper.kmp.activitypub.graphql.dataloader

import net.matsudamper.kmp.activitypub.graphql.otelSupplyAsync
import net.matsudamper.kmp.activitypub.logic.LikeService
import net.matsudamper.kmp.activitypub.logic.ViewerReaction
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

/**
 * [Key.viewer] が [Key.member] の投稿に付けたリアクション。付けていないか、絵文字の無いいいねなら値が無い
 */
class ViewerReactionDataLoaderDefine(
    private val likeService: LikeService?,
) : DataLoaderDefine<ViewerReactionDataLoaderDefine.Key, ViewerReaction> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<Key, ViewerReaction> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                val service = likeService ?: return@otelSupplyAsync mapOf()
                keys.groupBy { it.viewer }
                    .flatMap { (viewer, viewerKeys) ->
                        val reactions = service.viewerReactionsOf(viewer, viewerKeys.map { it.member }.toSet())
                        viewerKeys.mapNotNull { key -> reactions[key.member]?.let { key to it } }
                    }.toMap()
            }
        }
    }

    data class Key(
        val viewer: AccountId,
        val member: String,
    )
}
