package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.ProfileIconPaths
import net.matsudamper.kotpub.actor.ActorAppearance
import net.matsudamper.kotpub.actor.StoredActorAppearances

/**
 * @param iconAvailable 画像の置き場が無いと [AccountActorIcons] が返せないので、`icon` を出さない
 */
internal class AccountActorAppearances(
    private val accountService: AccountService,
    private val iconAvailable: Boolean,
) : StoredActorAppearances {
    override fun find(username: String): ActorAppearance {
        val iconPath = accountService.account(username)?.iconPath?.takeIf { iconAvailable }
        return ActorAppearance(
            links = listOf(),
            iconVersion = iconPath?.let { ProfileIconPaths.version(ImagePath(it)) },
            headerVersion = null,
        )
    }
}
