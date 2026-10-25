package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.screen.AndroidPreviewScreenPlatform
import net.matsudamper.kmp.activitypub.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun AdminContentPreview() {
    MaterialTheme {
        AdminContent(
            uiState = AdminScreenUiState(
                content = AdminScreenUiState.Content.LoggedIn(
                    sections = listOf(
                        AdminScreenUiState.MenuSection(
                            title = "ユーザー",
                            items = listOf(
                                previewMenuItem("ユーザーの一覧", "登録したユーザーを見る。"),
                                previewMenuItem("ユーザーの登録", "ログインして投稿できるユーザーを新しく作る。"),
                            ),
                        ),
                    ),
                    listener = AndroidPreviewLoggedInListener,
                ),
                listener = AndroidPreviewAdminListener,
            ),
            platform = AndroidPreviewScreenPlatform,
        )
    }
}

private fun previewMenuItem(title: String, description: String): AdminScreenUiState.MenuItem {
    return AdminScreenUiState.MenuItem(
        title = title,
        description = description,
        listener = object : AdminScreenUiState.MenuItem.Listener {
            override fun onClick() = Unit
        },
    )
}

private object AndroidPreviewLoggedInListener : AdminScreenUiState.Content.LoggedIn.Listener {
    override fun onClickLogout() = Unit
}

private object AndroidPreviewAdminListener : AdminScreenUiState.Listener {
    override fun onClickHome() = Unit

    override fun onClickAdmin() = Unit

    override fun onPasswordChanged(text: String) = Unit

    override fun onClickLogin() = Unit

    override fun onClickRetry() = Unit
}
