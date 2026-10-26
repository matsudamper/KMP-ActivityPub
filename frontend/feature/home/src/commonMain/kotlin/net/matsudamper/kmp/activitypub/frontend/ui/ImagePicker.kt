package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * 画像ファイルを選ばせる。返した関数を呼ぶと選ぶ画面を開き、選ばれたファイルの中身を [onPick] に渡す。
 * [maxBytes] を超えるファイルは最後まで読まずに [onTooLarge] を呼ぶ。大きな写真を丸ごと読むとメモリが足りなくなる。
 * ファイルを読めなければ [onReadError] を呼ぶ。選ばずに閉じたら何も呼ばない。
 * 選ばれたら読み始める前に [onReadStart] を呼ぶ。クラウド上の写真などは読み終えるまで時間がかかる
 */
@Composable
internal expect fun rememberImagePicker(
    maxBytes: Int,
    onReadStart: () -> Unit,
    onPick: (ByteArray) -> Unit,
    onTooLarge: () -> Unit,
    onReadError: () -> Unit,
): () -> Unit

/**
 * 中央を正方形に切り抜き、一辺 [sidePx] の PNG に描き直す。元が小さければ拡大しない。
 * 画像として読めないか、画素数が大きすぎて展開できなければ null
 */
internal expect fun ByteArray.toProfileIconOrNull(sidePx: Int): ProfileIconImage?

/**
 * @param png 送る画像。写真の位置情報などのメタデータは描き直したときに落ちる
 * @param bitmap [png] と同じ絵。プレビューに出す
 */
internal class ProfileIconImage(
    val png: ByteArray,
    val bitmap: ImageBitmap,
)
