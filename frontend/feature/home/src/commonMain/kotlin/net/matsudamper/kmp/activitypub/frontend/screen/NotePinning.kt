package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

/**
 * @param pinnableNoteId 固定できない投稿は null で、メニューに出さない
 * @param pinned 今固定されているか
 * @param onClick pinned は押した後に望む状態
 */
internal fun createPinMenuItems(
    pinnableNoteId: String?,
    pinned: Boolean,
    onClick: (pinnableNoteId: String, pinned: Boolean) -> Unit,
): List<PostItemUiState.MenuItem> {
    if (pinnableNoteId == null) return listOf()
    return listOf(
        PostItemUiState.MenuItem(
            label = if (pinned) "ピン留めを外す" else "ピン留め",
            listener = object : PostItemUiState.MenuItemListener {
                override fun onClick() {
                    onClick(pinnableNoteId, !pinned)
                }
            },
        ),
    )
}
