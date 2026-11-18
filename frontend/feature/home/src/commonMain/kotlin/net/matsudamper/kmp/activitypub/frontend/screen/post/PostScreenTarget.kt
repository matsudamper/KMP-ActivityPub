package net.matsudamper.kmp.activitypub.frontend.screen.post

/**
 * 投稿画面で何を書くか
 */
internal sealed interface PostScreenTarget {
    data object NewNote : PostScreenTarget

    /**
     * @param postId 引用するホームタイムラインの投稿の id
     */
    data class Quote(val postId: String) : PostScreenTarget

    /**
     * @param postId 返信するホームタイムラインの投稿の id
     */
    data class Reply(val postId: String) : PostScreenTarget

    /**
     * @param noteId 編集する自分の投稿の id。投稿の deletableNoteId
     */
    data class Edit(val noteId: String) : PostScreenTarget
}
