package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.logic.user.UserProfileResult
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

/**
 * @param onClick pinned は押した後に望む状態
 */
internal fun createPinMenuItems(
    post: UserProfileResult.Post,
    onClick: (pinnableNoteId: String, pinned: Boolean) -> Unit,
): List<PostItemUiState.MenuItem> {
    val pinnableNoteId = post.pinnableNoteId ?: return listOf()
    return listOf(
        PostItemUiState.MenuItem(
            label = if (post.pinned) "ピン留めを外す" else "ピン留め",
            listener = object : PostItemUiState.MenuItemListener {
                override fun onClick() {
                    onClick(pinnableNoteId, !post.pinned)
                }
            },
        ),
    )
}
