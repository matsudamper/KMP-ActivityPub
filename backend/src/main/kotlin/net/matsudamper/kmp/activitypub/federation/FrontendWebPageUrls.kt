package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kotpub.entity.PublicNoteId
import net.matsudamper.kotpub.note.NoteUrls
import net.matsudamper.kotpub.url.WebPageUrls

internal class FrontendWebPageUrls(
    private val domain: String,
) : WebPageUrls {
    override fun profile(username: String): String = "https://$domain/@$username"

    /**
     * ブラウザで開くと投稿の画面へ転送するので、投稿の id と同じ URL を返す
     */
    override fun note(
        username: String,
        publicId: PublicNoteId,
    ): String = NoteUrls(domain = domain, publicId = publicId).noteUrl
}
