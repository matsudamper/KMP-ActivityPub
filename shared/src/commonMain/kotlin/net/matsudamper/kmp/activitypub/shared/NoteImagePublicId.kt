package net.matsudamper.kmp.activitypub.shared

/**
 * 投稿の画像の URL に入る識別子。投稿の公開 id は配る文書を組み立てるときに決まるので、画像には別の識別子を付ける
 */
data class NoteImagePublicId(
    val value: String,
)
