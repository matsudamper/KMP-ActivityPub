package net.matsudamper.kmp.activitypub.graphql.resolver

import net.matsudamper.kmp.activitypub.graphql.model.QlAdminAccount
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kotpub.actor.ActorUrls

internal fun AccountService.ManagedAccount.toAdminGraphqlResponse(localDomain: String?): QlAdminAccount = QlAdminAccount(
    id = accountId,
    username = username,
    acct = localDomain?.let { ActorUrls(domain = it, username = username).mention },
    createdAt = createdAt.epochSecond,
)
