package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kotpub.actor.StoredActorNames

/**
 * kotpub は存在するアクターには必ず鍵がある前提なので、鍵を作る前に登録したアカウントはアクターとして扱わない
 */
internal class AccountActorNames(
    private val accountService: AccountService,
) : StoredActorNames {
    override fun find(username: String): String? {
        val account = accountService.account(username) ?: return null
        return account.username.takeIf { accountService.publicKeyPem(it) != null }
    }

    override fun finds(usernames: Set<String>): Map<String, String> =
        usernames.mapNotNull { username -> find(username)?.let { username to it } }.toMap()
}
