package net.matsudamper.kmp.activitypub.shared

/**
 * 投稿者が消した、他のサーバーの投稿の識別子
 */
data class DeletedRemoteNoteId(
    val value: Long,
)
