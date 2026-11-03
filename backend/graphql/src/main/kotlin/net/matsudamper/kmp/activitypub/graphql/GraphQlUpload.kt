package net.matsudamper.kmp.activitypub.graphql

/**
 * multipart で送られてきた `Upload` の中身。形式はここでは信じず、使う側が先頭のバイト列で決める
 */
class GraphQlUpload(
    val bytes: ByteArray,
)
