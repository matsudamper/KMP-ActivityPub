package net.matsudamper.kmp.activitypub.graphql.resolver

import net.matsudamper.kmp.activitypub.graphql.model.QlAdminAccount
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kotpub.actor.ActorUrls

internal fun Account.toAdminGraphqlResponse(localDomain: String?): QlAdminAccount = QlAdminAccount(
    username = username,
    acct = localDomain?.let { ActorUrls(domain = it, username = username).mention },
)
