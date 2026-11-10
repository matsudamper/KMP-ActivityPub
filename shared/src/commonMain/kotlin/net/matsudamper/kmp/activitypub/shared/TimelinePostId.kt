package net.matsudamper.kmp.activitypub.shared

/**
 * タイムラインに並ぶ投稿の識別子。このサーバーの投稿・他のサーバーの投稿・ブーストで載った投稿を区別できる
 */
data class TimelinePostId(
    val value: String,
)
