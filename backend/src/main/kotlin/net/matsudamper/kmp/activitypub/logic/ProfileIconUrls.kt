package net.matsudamper.kmp.activitypub.logic

/**
 * 画面に出すプロフィール画像の URL。Actor の `icon` と同じ URL を返す
 */
fun interface ProfileIconUrls {
    /**
     * プロフィール画像が無ければ null
     */
    fun find(username: String): String?
}
