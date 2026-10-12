package net.matsudamper.kmp.activitypub.frontend.screen.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ホームの中身はまだ無い。決まった文言だけを出す
 */
internal class HomeScreenViewModel {
    val uiStateFlow: StateFlow<HomeScreenUiState> =
        MutableStateFlow(
            HomeScreenUiState(
                message = "ホーム",
            ),
        ).asStateFlow()
}
