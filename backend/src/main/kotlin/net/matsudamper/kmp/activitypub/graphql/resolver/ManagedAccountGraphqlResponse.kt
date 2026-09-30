package net.matsudamper.kmp.activitypub.graphql.resolver

import net.matsudamper.kmp.activitypub.graphql.model.QlAdminAccount
import net.matsudamper.kmp.activitypub.graphql.model.QlUserAccount
import net.matsudamper.kmp.activitypub.logic.AccountService

internal fun AccountService.ManagedAccount.toAdminGraphqlResponse(): QlAdminAccount = QlAdminAccount(
    id = accountId,
    username = urls.username,
    acct = urls.mention,
    createdAt = createdAt.epochSecond,
)

internal fun AccountService.ManagedAccount.toUserGraphqlResponse(): QlUserAccount = QlUserAccount(
    username = urls.username,
    acct = urls.mention,
)
