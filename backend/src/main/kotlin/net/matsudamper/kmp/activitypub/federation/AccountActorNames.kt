package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kotpub.actor.StoredActorNames

internal class AccountActorNames(
    private val accountService: AccountService,
) : StoredActorNames {
    override fun find(username: String): String? = accountService.account(username)?.username

    override fun finds(usernames: Set<String>): Map<String, String> =
        usernames.mapNotNull { username -> find(username)?.let { username to it } }.toMap()
}
