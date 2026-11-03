package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId

/**
 * 投稿に付けた画像の URL。他のサーバーにも画面にも同じものを見せる。
 * 相手は投稿を受け取った後に画像を取りに来るので、期限の付く置き場の URL は使わない
 */
class NoteImageUrls(
    private val domain: String,
) {
    fun url(publicId: NoteImagePublicId): String = "https://$domain$PATH_PREFIX/${publicId.value}"

    companion object {
        const val PATH_PREFIX: String = "/media"
    }
}
