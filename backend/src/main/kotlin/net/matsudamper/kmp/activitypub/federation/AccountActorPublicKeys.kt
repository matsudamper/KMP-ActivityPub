package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kotpub.actor.StoredActorPublicKeys

internal class AccountActorPublicKeys(
    private val accountService: AccountService,
) : StoredActorPublicKeys {
    override fun find(username: String): String =
        checkNotNull(accountService.publicKeyPem(username)) { "AccountActorNames が返したアカウントに公開鍵が無い: $username" }
}
