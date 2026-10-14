package net.matsudamper.kmp.activitypub.android

import kotlinx.serialization.Serializable

/**
 * Set-Cookie を受け取った接続先 URL と、Cookie の文字列表現の組
 */
@Serializable
internal data class StoredCookieEntry(
    val origin: String,
    val value: String,
)
