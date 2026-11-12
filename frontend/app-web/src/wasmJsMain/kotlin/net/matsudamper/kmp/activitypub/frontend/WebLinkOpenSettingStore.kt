package net.matsudamper.kmp.activitypub.frontend

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.matsudamper.kmp.activitypub.frontend.logic.link.LinkOpenMode
import net.matsudamper.kmp.activitypub.frontend.logic.link.LinkOpenSettingStore

/**
 * ブラウザにはカスタムタブが無いので、常にブラウザの新しいタブで開く
 */
internal class WebLinkOpenSettingStore : LinkOpenSettingStore {
    override val selectableModes: List<LinkOpenMode> = listOf()

    override val linkOpenModeFlow: Flow<LinkOpenMode> = flowOf(LinkOpenMode.ExternalBrowser)

    override suspend fun save(linkOpenMode: LinkOpenMode): Boolean = false
}
