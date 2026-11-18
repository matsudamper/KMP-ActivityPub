package net.matsudamper.kmp.activitypub.frontend.screen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserEditableNoteBodyResult
import net.matsudamper.kmp.activitypub.frontend.ui.EditNoteDialogUiState

/**
 * 自分の投稿の編集を、下書きを読むところから保存し終えるまで。投稿を出す画面ごとに持つ
 *
 * @param onMessage 編集できたときと、できなかったときに出す文
 * @param onEdited 編集できたとき。画面に出している本文を読み直す
 */
internal class NoteEditor(
    private val api: UserApi,
    private val coroutineScope: CoroutineScope,
    private val onMessage: (String) -> Unit,
    private val onEdited: (noteId: String) -> Unit,
) {
    private val mutableStateFlow = MutableStateFlow<NoteEditingState>(NoteEditingState.None)
    val stateFlow: StateFlow<NoteEditingState> = mutableStateFlow.asStateFlow()

    private val dialogListener = object : EditNoteDialogUiState.Listener {
        override fun onTextChange(text: String) {
            mutableStateFlow.update { state -> if (state is NoteEditingState.Editing) state.copy(text = text) else state }
        }

        override fun onClickSave() {
            save()
        }

        override fun onDismiss() {
            // 保存している途中で閉じると、結果が出る前に別の投稿を選べてしまうので閉じない
            mutableStateFlow.update { state -> if (state is NoteEditingState.Saving) state else NoteEditingState.None }
        }
    }

    /**
     * @param noteId 投稿の deletableNoteId
     */
    fun start(noteId: String) {
        if (mutableStateFlow.value != NoteEditingState.None) return
        mutableStateFlow.value = NoteEditingState.Loading(noteId)
        coroutineScope.launch {
            val result = api.editableNoteBody(noteId)
            // 読んでいる間に閉じられたら下書きを出さない
            if (mutableStateFlow.value != NoteEditingState.Loading(noteId)) return@launch
            when (result) {
                is UserEditableNoteBodyResult.Success -> mutableStateFlow.value = NoteEditingState.Editing(noteId = noteId, text = result.body)

                is UserEditableNoteBodyResult.Failure -> {
                    mutableStateFlow.value = NoteEditingState.None
                    onMessage(result.message)
                }
            }
        }
    }

    fun createDialogUiState(state: NoteEditingState): EditNoteDialogUiState? = when (state) {
        NoteEditingState.None -> null

        is NoteEditingState.Loading -> EditNoteDialogUiState(
            text = "",
            textFieldEnabled = false,
            saveButtonEnabled = false,
            closeEnabled = true,
            loadingIndicatorVisible = true,
            listener = dialogListener,
        )

        is NoteEditingState.Editing -> EditNoteDialogUiState(
            text = state.text,
            textFieldEnabled = true,
            saveButtonEnabled = true,
            closeEnabled = true,
            loadingIndicatorVisible = false,
            listener = dialogListener,
        )

        is NoteEditingState.Saving -> EditNoteDialogUiState(
            text = state.text,
            textFieldEnabled = false,
            saveButtonEnabled = false,
            closeEnabled = false,
            loadingIndicatorVisible = false,
            listener = dialogListener,
        )
    }

    private fun save() {
        val editing = mutableStateFlow.value as? NoteEditingState.Editing ?: return
        mutableStateFlow.value = NoteEditingState.Saving(noteId = editing.noteId, text = editing.text)
        coroutineScope.launch {
            val error = api.editNote(noteId = editing.noteId, body = editing.text)
            if (error == null) {
                mutableStateFlow.value = NoteEditingState.None
                onMessage("投稿を編集しました")
                onEdited(editing.noteId)
            } else {
                // 書いた本文を失わないよう、ダイアログを開いたまま書き直せるようにする
                mutableStateFlow.value = editing
                onMessage(error)
            }
        }
    }
}

internal sealed interface NoteEditingState {
    data object None : NoteEditingState

    /**
     * 下書きにする今の本文を読んでいる
     */
    data class Loading(
        val noteId: String,
    ) : NoteEditingState

    data class Editing(
        val noteId: String,
        val text: String,
    ) : NoteEditingState

    data class Saving(
        val noteId: String,
        val text: String,
    ) : NoteEditingState
}
