package net.matsudamper.kmp.activitypub.shared

/**
 * 投稿の版の識別子。同じ投稿の版の中でだけ一意
 */
data class PostRevisionId(
    val value: String,
)
