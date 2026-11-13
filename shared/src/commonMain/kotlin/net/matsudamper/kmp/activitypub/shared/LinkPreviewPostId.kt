package net.matsudamper.kmp.activitypub.shared

/**
 * OGP を引く投稿の識別子。タイムラインの投稿の id か、このサーバーの投稿を公開 id で指す id のどちらかが入る
 */
data class LinkPreviewPostId(
    val value: String,
)
