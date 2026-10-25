package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * 画像ファイルを選ばせる。返した関数を呼ぶと選ぶ画面を開き、選ばれたファイルの中身を [onPick] に渡す。
 * [maxBytes] を超えるファイルは最後まで読まずに [onTooLarge] を呼ぶ。大きな写真を丸ごと読むとメモリが足りなくなる。
 * 選ばずに閉じたら何も呼ばない
 */
@Composable
internal expect fun rememberImagePicker(
    maxBytes: Int,
    onPick: (ByteArray) -> Unit,
    onTooLarge: () -> Unit,
): () -> Unit

/**
 * 画像として読めなければ null
 */
internal expect fun ByteArray.decodeToImageBitmapOrNull(): ImageBitmap?
