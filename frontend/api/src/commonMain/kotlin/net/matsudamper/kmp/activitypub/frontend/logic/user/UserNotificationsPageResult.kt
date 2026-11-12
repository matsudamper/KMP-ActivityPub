package net.matsudamper.kmp.activitypub.frontend.logic.user

import net.matsudamper.kmp.activitypub.frontend.graphql.NotificationsQuery

sealed interface UserNotificationsPageResult {
    data class Success(val data: NotificationsQuery.Data) : UserNotificationsPageResult
    data class Failure(val message: String) : UserNotificationsPageResult
}
