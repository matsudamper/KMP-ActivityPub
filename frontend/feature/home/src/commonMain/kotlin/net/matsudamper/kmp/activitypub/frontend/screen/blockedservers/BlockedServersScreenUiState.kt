package net.matsudamper.kmp.activitypub.frontend.screen.blockedservers

import androidx.compose.runtime.Immutable

data class BlockedServersScreenUiState(
    val serverInput: String,
    val serverInputEnabled: Boolean,
    val blockButtonEnabled: Boolean,
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * @param servers 新しくブロックした順
         */
        data class Loaded(
            val servers: List<Server>,
            val emptyMessageVisible: Boolean,
        ) : Content

        /**
         * 読めなかった理由は Snackbar で出すので、ここでは読み直す操作だけを出す
         */
        data object LoadFailed : Content
    }

    data class Server(
        val host: String,
        val unblockButtonEnabled: Boolean,
        val listener: ServerListener,
    )

    @Immutable
    interface ServerListener {
        fun onClickUnblock()
    }

    @Immutable
    interface Listener {
        fun onClickBack()

        fun onChangeServerInput(value: String)

        fun onClickBlock()

        fun onClickRetry()
    }
}
