package net.matsudamper.kmp.activitypub.graphql.resolver

import net.matsudamper.kmp.activitypub.graphql.model.QlFollowingGroup
import net.matsudamper.kmp.activitypub.repository.FollowingGroup

internal fun FollowingGroup.toQlFollowingGroup(): QlFollowingGroup = QlFollowingGroup(id = id, name = name)
