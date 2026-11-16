package net.matsudamper.kmp.activitypub.logic

/**
 * 画面に出すプロフィールヘッダーの URL。Actor の `image` と同じ URL を返す
 */
fun interface ProfileHeaderUrls {
    /**
     * ヘッダーが無ければ null
     */
    fun find(username: String): String?
}
