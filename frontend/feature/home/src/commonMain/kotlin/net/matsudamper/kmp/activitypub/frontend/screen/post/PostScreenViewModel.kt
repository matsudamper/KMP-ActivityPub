package net.matsudamper.kmp.activitypub.frontend.screen.post

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 投稿の中身はまだ無い。決まった文言だけを出す
 */
internal class PostScreenViewModel {
    val uiStateFlow: StateFlow<PostScreenUiState> =
        MutableStateFlow(
            PostScreenUiState(
                message = "投稿",
            ),
        ).asStateFlow()
}
