package net.matsudamper.kmp.activitypub.graphql.dataloader

import net.matsudamper.kmp.activitypub.graphql.otelSupplyAsync
import net.matsudamper.kmp.activitypub.logic.NotePinService
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

/**
 * このサーバーの投稿がプロフィールに固定されているか
 */
class PinnedNoteDataLoaderDefine(
    private val notePinService: NotePinService,
) : DataLoaderDefine<PublicNoteId, Boolean> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<PublicNoteId, Boolean> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                val pinned = notePinService.pinnedAmong(keys)
                keys.associateWith { it in pinned }
            }
        }
    }
}
