package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.ProfileHeaderPaths
import net.matsudamper.kmp.activitypub.logic.ProfileIconPaths
import net.matsudamper.kotpub.actor.ActorAppearance
import net.matsudamper.kotpub.actor.ActorLink
import net.matsudamper.kotpub.actor.StoredActorAppearances

/**
 * @param imageAvailable 画像の置き場が無いと [AccountActorIcons] と [AccountActorHeaders] が返せないので、`icon` と `image` を出さない
 */
internal class AccountActorAppearances(
    private val accountService: AccountService,
    private val imageAvailable: Boolean,
) : StoredActorAppearances {
    override fun find(username: String): ActorAppearance {
        val account = accountService.account(username)?.takeIf { imageAvailable }
        val iconPath = account?.iconPath
        val headerPath = account?.headerPath
        return ActorAppearance(
            links = accountService.profileDetail(username)?.links.orEmpty().map { ActorLink(name = it.name, url = it.url) },
            iconVersion = iconPath?.let { ProfileIconPaths.version(ImagePath(it)) },
            headerVersion = headerPath?.let { ProfileHeaderPaths.version(ImagePath(it)) },
        )
    }
}
