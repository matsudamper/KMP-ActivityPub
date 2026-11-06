package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * 返した関数に URL を渡すと、端末の共有の画面を開く
 *
 * @param onCopiedToClipboard 共有の画面を出せずに、代わりにクリップボードへ写したとき。何も出さないと押しても何も起きないように見える
 */
@Composable
internal expect fun rememberUrlSharer(onCopiedToClipboard: () -> Unit): (url: String) -> Unit
