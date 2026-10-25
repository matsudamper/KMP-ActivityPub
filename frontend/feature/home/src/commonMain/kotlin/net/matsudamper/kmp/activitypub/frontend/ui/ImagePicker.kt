package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * 画像ファイルを選ばせる。返した関数を呼ぶと選ぶ画面を開き、選ばれたファイルの中身を [onPick] に渡す。
 * 選ばずに閉じたら何も呼ばない
 */
@Composable
internal expect fun rememberImagePicker(onPick: (ByteArray) -> Unit): () -> Unit

/**
 * 画像として読めなければ null
 */
internal expect fun ByteArray.decodeToImageBitmapOrNull(): ImageBitmap?
