package net.matsudamper.kmp.activitypub.graphql.resolver

import net.matsudamper.kmp.activitypub.graphql.model.QlBlockedAccount
import net.matsudamper.kmp.activitypub.repository.BlockedActor

internal fun BlockedActor.toQlBlockedAccount(): QlBlockedAccount = QlBlockedAccount(actorUri = actorUri, acct = acct)
