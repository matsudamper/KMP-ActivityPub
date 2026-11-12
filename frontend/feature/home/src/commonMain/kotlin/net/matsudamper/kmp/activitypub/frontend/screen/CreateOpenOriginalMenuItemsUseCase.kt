package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import org.koin.core.annotation.Singleton

@Singleton
internal class CreateOpenOriginalMenuItemsUseCase {
    /**
     * @param originalUrl 投稿の URL が作れなければ null で、メニューに出さない
     */
    operator fun invoke(
        originalUrl: String?,
        onClick: (url: String) -> Unit,
    ): List<PostItemUiState.MenuItem> {
        if (originalUrl == null) return listOf()
        return listOf(
            PostItemUiState.MenuItem(
                label = "オリジナルを開く",
                listener = object : PostItemUiState.MenuItemListener {
                    override fun onClick() {
                        onClick(originalUrl)
                    }
                },
            ),
        )
    }
}
