package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * 画面を開いている間、クリップボードから貼り付けられた画像を [onPaste] に渡す。
 * 画像のない貼り付けは邪魔しない
 */
@Composable
internal expect fun ImagePasteHandler(onPaste: (PickedImageFile) -> Unit)
