package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kotpub.entity.PublicNoteId
import net.matsudamper.kotpub.note.NoteUrls
import net.matsudamper.kotpub.url.WebPageUrls

/**
 * フロントエンドの画面の URL
 */
internal class FrontendWebPageUrls(
    private val domain: String,
) : WebPageUrls {
    override fun profile(username: String): String = "https://$domain/@$username"

    /**
     * 投稿 1 件の画面がまだ無いので、`url` を出さなかったときと同じ投稿の id を返す
     */
    override fun note(
        username: String,
        publicId: PublicNoteId,
    ): String = NoteUrls(domain = domain, publicId = publicId).noteUrl
}
