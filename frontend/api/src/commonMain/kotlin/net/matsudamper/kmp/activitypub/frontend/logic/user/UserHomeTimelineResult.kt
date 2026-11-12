package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserHomeTimelineResult {
    data class Success(val posts: List<Post>, val nextCursor: String?) : UserHomeTimelineResult
    data object NotLoggedIn : UserHomeTimelineResult

    data class Post(
        val id: String,
        /**
         * 投稿者が投稿を消したか、ブーストした相手がブーストを取り消したか。ほかの項目は消される前のまま
         */
        val deleted: Boolean,
        /**
         * タイムラインに並べる時刻。複数の購読先を 1 つのカラムに混ぜるときに、新しい順に並べるのに使う
         */
        val sortedAt: String,
        val username: String,
        val host: String?,
        val displayName: String,
        val displayNameEmojis: List<UserCustomEmoji>,
        /**
         * 投稿者のプロフィール画像が無ければ null
         */
        val iconUrl: String?,
        val body: UserPostBody,
        /**
         * [UserApi.setLiked] と [UserApi.setBoosted] に渡す id。ブーストで載った投稿は、ブーストされた元の投稿を指す
         */
        val reactionPostId: String,
        /**
         * ログイン中のアカウントの投稿なら [UserApi.deleteNote] に渡す id。消せない投稿は null
         */
        val deletableNoteId: String?,
        /**
         * ログイン中のアカウントの投稿なら [UserApi.setPinned] に渡す id。固定できない投稿は null
         */
        val pinnableNoteId: String?,
        /**
         * プロフィールの先頭に固定されているか
         */
        val pinned: Boolean,
        /**
         * フォローしている相手のブーストとして載った投稿なら、ブーストした相手。それ以外は null
         */
        val boostedBy: Actor?,
        /**
         * 投稿が載った購読先のグループ。ホームなら null
         */
        val followingGroupId: String?,
    )

    /**
     * ブーストした相手や、通知を送ってきた相手
     */
    data class Actor(
        val username: String,
        /**
         * このサーバーのアカウントなら null
         */
        val host: String?,
        val displayName: String,
        val displayNameEmojis: List<UserCustomEmoji>,
        /**
         * プロフィール画像が無ければ null
         */
        val iconUrl: String?,
    )
}
