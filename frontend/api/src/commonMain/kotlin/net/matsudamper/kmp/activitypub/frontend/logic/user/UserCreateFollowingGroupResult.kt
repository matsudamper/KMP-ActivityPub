package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserCreateFollowingGroupResult {
    data class Created(
        val group: UserFollowingGroup,
    ) : UserCreateFollowingGroupResult

    data class Failure(
        val message: String,
    ) : UserCreateFollowingGroupResult
}
