package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * Android のキーボードの貼り付けは入力欄に文字として届くだけなので、受け取れない
 */
@Composable
internal actual fun ImagePasteHandler(onPaste: (PickedImageFile) -> Unit) = Unit
