package net.matsudamper.kmp.activitypub.frontend.screen.reaction

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun ReactionPickerUnicodePreview() {
    MaterialTheme {
        ReactionPickerContent(
            uiState = ReactionPickerUiState(
                searchQuery = "",
                selectedTab = ReactionPickerUiState.Tab.Unicode,
                content = ReactionPickerUiState.Content.UnicodeEmojis(
                    sections = UnicodeEmojis.all.groupBy { it.category }.entries.take(2).map { (category, emojis) ->
                        ReactionPickerUiState.UnicodeSection(
                            title = category.label,
                            items = emojis.take(24).map { ReactionPickerUiState.UnicodeEmojiItem(emoji = it.emoji, listener = AndroidPreviewItemListener) },
                        )
                    },
                    emptyMessageVisible = false,
                ),
                listener = AndroidPreviewListener,
            ),
            removeButtonVisible = true,
            dialogListener = AndroidPreviewDialogListener,
        )
    }
}

@Preview
@Composable
private fun ReactionPickerSearchPreview() {
    MaterialTheme {
        ReactionPickerContent(
            uiState = ReactionPickerUiState(
                searchQuery = "ねこ",
                selectedTab = ReactionPickerUiState.Tab.Unicode,
                content = ReactionPickerUiState.Content.UnicodeEmojis(
                    sections = listOf(
                        ReactionPickerUiState.UnicodeSection(
                            title = null,
                            items = UnicodeEmojis.search("ねこ").map { ReactionPickerUiState.UnicodeEmojiItem(emoji = it.emoji, listener = AndroidPreviewItemListener) },
                        ),
                    ),
                    emptyMessageVisible = false,
                ),
                listener = AndroidPreviewListener,
            ),
            removeButtonVisible = false,
            dialogListener = AndroidPreviewDialogListener,
        )
    }
}

@Preview
@Composable
private fun ReactionPickerCustomEmptyPreview() {
    MaterialTheme {
        ReactionPickerContent(
            uiState = ReactionPickerUiState(
                searchQuery = "",
                selectedTab = ReactionPickerUiState.Tab.CustomEmoji,
                content = ReactionPickerUiState.Content.CustomEmojis(
                    items = listOf(),
                    emptyMessage = "カスタム絵文字はまだありません。設定から追加できます",
                ),
                listener = AndroidPreviewListener,
            ),
            removeButtonVisible = false,
            dialogListener = AndroidPreviewDialogListener,
        )
    }
}

private object AndroidPreviewItemListener : ReactionPickerUiState.ItemListener {
    override fun onClick() = Unit
}

private object AndroidPreviewListener : ReactionPickerUiState.Listener {
    override fun onChangeSearchQuery(value: String) = Unit

    override fun onSelectTab(tab: ReactionPickerUiState.Tab) = Unit

    override fun onClickRetry() = Unit
}

private object AndroidPreviewDialogListener : ReactionPickerDialogUiState.Listener {
    override fun onPick(reaction: PickedReaction) = Unit

    override fun onClickRemove() = Unit

    override fun onDismiss() = Unit
}
