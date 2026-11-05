package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import coil3.PlatformContext
import coil3.request.ImageRequest
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserNoteImageContent

/**
 * 画像ファイルを選ばせる。返した関数を呼ぶと選ぶ画面を開き、選ばれたファイルを [onPick] に渡す。
 * 選ばずに閉じたら何も呼ばない
 */
@Composable
internal expect fun rememberImagePicker(onPick: (PickedImageFile) -> Unit): () -> Unit

/**
 * 選ばれた画像ファイル。
 *
 * 読むのは受け取った側のスコープで行う。クラウド上の写真などは読み終えるまで時間がかかり、
 * 画面のスコープで読むと回転などで作り直されたときに止まって、結果が届かなくなる
 */
interface PickedImageFile {
    /**
     * [maxBytes] を超えるファイルは最後まで読まずに [ReadResult.TooLarge] を返す。大きな写真を丸ごと読むとメモリが足りなくなる
     */
    suspend fun read(maxBytes: Int): ReadResult

    /**
     * 縮めずに品質 100 の WebP に描き直す。書き出す WebP には EXIF などのメタデータを書き込まないので、写真の向きは画素に反映しておく。
     * 書き出した WebP が [maxBytes] を超えたら [WebpResult.TooLarge]。
     * 展開と書き出しは画面を止めるほど重いので、画面のスレッドの外で行う。元のファイルも書き出した WebP も丸ごとメモリには持たない
     */
    suspend fun toWebp(maxBytes: Int): WebpResult

    sealed interface ReadResult {
        class Success(
            val bytes: ByteArray,
        ) : ReadResult

        data object TooLarge : ReadResult

        data object Error : ReadResult
    }
}

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

sealed interface WebpResult {
    class Success(
        val image: WebpImage,
    ) : WebpResult

    data object TooLarge : WebpResult

    /**
     * 原寸で展開するとメモリが足りなくなる
     */
    data object TooManyPixels : WebpResult

    data object Unreadable : WebpResult
}

/**
 * 書き出した WebP。何枚も選んだまま投稿するまで持つので、Android はファイル、Web は Blob に置く
 */
interface WebpImage {
    fun previewRequest(context: PlatformContext): ImageRequest

    /**
     * Web は送る間だけ中身をメモリに読む
     */
    suspend fun openContent(): UserNoteImageContent

    /**
     * 外したか投稿し終えたら呼ぶ。送っている途中で呼ぶと送れなくなる
     */
    fun release()
}
