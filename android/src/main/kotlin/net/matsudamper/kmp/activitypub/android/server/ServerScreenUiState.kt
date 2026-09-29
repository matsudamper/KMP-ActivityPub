package net.matsudamper.kmp.activitypub.android.server

import androidx.compose.runtime.Immutable

/**
 * 接続先のサーバーを入れる画面
 *
 * @param error 入力欄の下に赤字で出す。null なら何も出さない
 */
data class ServerScreenUiState(
    val serverUrl: String,
    val error: String?,
    val connectButtonEnabled: Boolean,
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onServerUrlChanged(text: String)

        fun onClickConnect()
    }
}
