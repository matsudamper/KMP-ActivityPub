package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * 購読を仕分けるグループ。どのグループにも入れていない購読はメインに入る
 */
data class UserFollowingGroup(
    val id: String,
    val name: String,
)
