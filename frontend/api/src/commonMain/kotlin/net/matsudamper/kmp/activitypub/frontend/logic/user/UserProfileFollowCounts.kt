package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * @param following 他のサーバーのユーザーが数を出していないか、読めなければ null
 * @param followers 他のサーバーのユーザーが数を出していないか、読めなければ null
 */
data class UserProfileFollowCounts(
    val following: Int?,
    val followers: Int?,
)
