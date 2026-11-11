package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.compose.runtime.Immutable

data class AdminScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data class Loaded(
            val remoteProfileRefresh: RemoteProfileRefresh,
        ) : Content

        data class Message(
            val message: String,
        ) : Content
    }

    /**
     * 他のサーバーのユーザー全員のプロフィールを取り直す
     */
    data class RemoteProfileRefresh(
        val statusText: String,
        val startButtonEnabled: Boolean,
        val listener: RemoteProfileRefreshListener,
    )

    @Immutable
    interface RemoteProfileRefreshListener {
        fun onClickStart()
    }

    @Immutable
    interface Listener {
        fun onClickBack()
    }
}
