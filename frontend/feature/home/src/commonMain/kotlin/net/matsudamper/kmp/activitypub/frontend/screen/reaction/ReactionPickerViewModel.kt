package net.matsudamper.kmp.activitypub.frontend.screen.reaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserCustomEmojisResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLocalCustomEmoji
import org.koin.core.annotation.KoinViewModel

/**
 * リアクションの絵文字を選ぶ。開くたびに作り直す
 */
@KoinViewModel
internal class ReactionPickerViewModel(
    private val api: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                searchQuery = "",
                selectedTab = ReactionPickerUiState.Tab.CustomEmoji,
                customEmojis = CustomEmojisState.Loading,
            ),
        )

    private val listener = object : ReactionPickerUiState.Listener {
        override fun onChangeSearchQuery(value: String) {
            viewModelStateFlow.update { it.copy(searchQuery = value) }
        }

        override fun onSelectTab(tab: ReactionPickerUiState.Tab) {
            viewModelStateFlow.update { it.copy(selectedTab = tab) }
        }

        override fun onClickRetry() {
            loadCustomEmojis()
        }
    }

    val uiStateFlow: StateFlow<ReactionPickerUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { uiStateFlow.value = createUiState(it) }
            }
        }.asStateFlow()

    init {
        loadCustomEmojis()
    }

    private fun loadCustomEmojis() {
        viewModelStateFlow.update { it.copy(customEmojis = CustomEmojisState.Loading) }
        viewModelScope.launch {
            val customEmojis = when (val result = api.customEmojis()) {
                is UserCustomEmojisResult.Success -> CustomEmojisState.Loaded(result.emojis)
                is UserCustomEmojisResult.Failure -> CustomEmojisState.Failed(result.message)
            }
            viewModelStateFlow.update { it.copy(customEmojis = customEmojis) }
        }
    }

    private fun pick(reaction: PickedReaction) {
        viewModelScope.launch { events.send { it.pick(reaction) } }
    }

    private fun createUiState(viewModelState: ViewModelState): ReactionPickerUiState {
        return ReactionPickerUiState(
            searchQuery = viewModelState.searchQuery,
            selectedTab = viewModelState.selectedTab,
            content = when (viewModelState.selectedTab) {
                ReactionPickerUiState.Tab.CustomEmoji -> createCustomEmojiContent(viewModelState)
                ReactionPickerUiState.Tab.Unicode -> createUnicodeContent(viewModelState.searchQuery)
            },
            listener = listener,
        )
    }

    private fun createCustomEmojiContent(viewModelState: ViewModelState): ReactionPickerUiState.Content {
        return when (val customEmojis = viewModelState.customEmojis) {
            CustomEmojisState.Loading -> ReactionPickerUiState.Content.Loading

            is CustomEmojisState.Failed -> ReactionPickerUiState.Content.Error(customEmojis.message)

            is CustomEmojisState.Loaded -> {
                val query = EmojiSearchText.normalize(viewModelState.searchQuery.trim().trim(':'))
                val items = customEmojis.emojis
                    .filter { EmojiSearchText.normalize(it.shortcode).contains(query) }
                    .map { emoji ->
                        ReactionPickerUiState.CustomEmojiItem(
                            shortcode = emoji.shortcode,
                            imageUrl = emoji.imageUrl,
                            listener = object : ReactionPickerUiState.ItemListener {
                                override fun onClick() {
                                    pick(PickedReaction.Custom(shortcode = emoji.shortcode, imageUrl = emoji.imageUrl))
                                }
                            },
                        )
                    }
                ReactionPickerUiState.Content.CustomEmojis(
                    items = items,
                    emptyMessage = when {
                        items.isNotEmpty() -> null
                        customEmojis.emojis.isEmpty() -> "カスタム絵文字はまだありません。設定から追加できます"
                        else -> "見つかりませんでした"
                    },
                )
            }
        }
    }

    private fun createUnicodeContent(searchQuery: String): ReactionPickerUiState.Content {
        val searching = searchQuery.isNotBlank()
        val emojis = UnicodeEmojis.search(searchQuery)
        val sections = if (searching) {
            listOf(ReactionPickerUiState.UnicodeSection(title = null, items = emojis.map { it.toItem() }))
        } else {
            emojis.groupBy { it.category }.map { (category, categoryEmojis) ->
                ReactionPickerUiState.UnicodeSection(title = category.label, items = categoryEmojis.map { it.toItem() })
            }
        }
        return ReactionPickerUiState.Content.UnicodeEmojis(sections = sections, emptyMessageVisible = emojis.isEmpty())
    }

    private fun UnicodeEmoji.toItem(): ReactionPickerUiState.UnicodeEmojiItem = ReactionPickerUiState.UnicodeEmojiItem(
        emoji = emoji,
        listener = object : ReactionPickerUiState.ItemListener {
            override fun onClick() {
                pick(PickedReaction.Unicode(emoji))
            }
        },
    )

    internal interface Event {
        fun pick(reaction: PickedReaction)
    }

    private data class ViewModelState(
        val searchQuery: String,
        val selectedTab: ReactionPickerUiState.Tab,
        val customEmojis: CustomEmojisState,
    )

    private sealed interface CustomEmojisState {
        data object Loading : CustomEmojisState

        data class Loaded(val emojis: List<UserLocalCustomEmoji>) : CustomEmojisState

        data class Failed(val message: String) : CustomEmojisState
    }
}
