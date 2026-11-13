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

/**
 * 1 回の応答の中で同じユーザーを何度も引かないように覚えておく。画像を変えても次の応答には反映されるよう、応答ごとに作る
 */
fun ProfileIconUrls.memoized(): ProfileIconUrls {
    val iconUrlsByUsername = mutableMapOf<String, String?>()
    return ProfileIconUrls { username ->
        if (username in iconUrlsByUsername) {
            iconUrlsByUsername[username]
        } else {
            find(username).also { iconUrlsByUsername[username] = it }
        }
    }
}
