package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

/**
 * 投稿の削除の確かめから消し終わるまで。投稿を出す画面ごとに持つ
 */
internal sealed interface NoteDeletionState {
    data object None : NoteDeletionState

    /**
     * 確かめのダイアログを出している
     */
    data class Confirming(
        val noteId: String,
    ) : NoteDeletionState

    data class Deleting(
        val noteId: String,
    ) : NoteDeletionState
}

/**
 * @param deletableNoteId 消せない投稿は null で、メニューを出さない
 */
internal fun createNoteMenuItems(
    deletableNoteId: String?,
    onClickDelete: (noteId: String) -> Unit,
): List<PostItemUiState.MenuItem> {
    if (deletableNoteId == null) return listOf()
    return listOf(
        PostItemUiState.MenuItem(
            label = "削除",
            listener = object : PostItemUiState.MenuItemListener {
                override fun onClick() {
                    onClickDelete(deletableNoteId)
                }
            },
        ),
    )
}

/**
 * 消している間に別の投稿を選べないよう、消している途中は確かめ直さない
 */
internal fun NoteDeletionState.confirm(noteId: String): NoteDeletionState =
    if (this is NoteDeletionState.Deleting) this else NoteDeletionState.Confirming(noteId)

/**
 * 消している途中で閉じると、結果が出る前に別の投稿を選べてしまうので閉じない
 */
internal fun NoteDeletionState.dismiss(): NoteDeletionState =
    if (this is NoteDeletionState.Confirming) NoteDeletionState.None else this

internal fun NoteDeletionState.toDialogUiState(listener: DeleteNoteDialogUiState.Listener): DeleteNoteDialogUiState? {
    return when (this) {
        NoteDeletionState.None -> null
        is NoteDeletionState.Confirming -> DeleteNoteDialogUiState(deleteButtonEnabled = true, listener = listener)
        is NoteDeletionState.Deleting -> DeleteNoteDialogUiState(deleteButtonEnabled = false, listener = listener)
    }
}
