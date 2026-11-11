package net.matsudamper.kmp.activitypub.frontend.logic.user

import kotlin.time.Instant

sealed interface UserProfileResult {
    /**
     * @param iconUrl プロフィール画像が無ければ null
     * @param headerUrl ヘッダー画像が無ければ null
     * @param actorUri フォローできない相手（このサーバーのユーザー）は null
     * @param viewerIsOwner ログイン中のアカウント本人のプロフィールか
     */
    data class Found(
        val actorUri: String?,
        val username: String,
        val host: String,
        val displayName: String,
        val displayNameEmojis: List<UserCustomEmoji>,
        val iconUrl: String?,
        val following: Boolean,
        val followingGroupId: String?,
        val headerUrl: String?,
        val biography: String?,
        val links: List<UserProfileLink>,
        val posts: List<Post>,
        val viewerIsOwner: Boolean,
    ) : UserProfileResult

    data object NotFound : UserProfileResult

    /**
     * @param linkPreviewId OGP を [UserApi.linkPreviews] で取るときの id。取れない投稿は null
     * @param postId [UserApi.postDetail] で個別投稿画面を開くときの id。開けない投稿は null
     * @param deletableNoteId ログイン中のアカウントの投稿なら [UserApi.deleteNote] に渡す id。消せない投稿は null
     * @param pinned プロフィールの先頭に固定されているか
     * @param pinnableNoteId ログイン中のアカウントのプロフィールの投稿なら [UserApi.setPinned] に渡す id。固定できない投稿は null
     * @param likePostId [UserApi.setLiked] に渡す id。いいねできない投稿は null
     * @param reactionPostId [UserApi.setBoosted] に渡す id。本人がブーストした投稿は、ブーストされた元の投稿を指す。付け外しできない投稿は null
     * @param boostedNoteAuthor プロフィールの本人がブーストした投稿なら、その投稿者。本人の投稿は null
     */
    data class Post(
        val linkPreviewId: String?,
        val postId: String?,
        val deletableNoteId: String?,
        val pinned: Boolean,
        val pinnableNoteId: String?,
        val likePostId: String?,
        val reactionPostId: String?,
        val body: UserPostBody,
        val boostedNoteAuthor: Author?,
    )

    /**
     * @param iconUrl プロフィール画像が無ければ null
     */
    data class Author(
        val username: String,
        val host: String,
        val displayName: String,
        val displayNameEmojis: List<UserCustomEmoji>,
        val iconUrl: String?,
    )

    data class Failure(
        val message: String,
    ) : UserProfileResult
}

/**
 * いいねを変えた結果を、取得し直さずに反映する
 */
fun UserProfileResult.Found.withLiked(likePostId: String, liked: Boolean): UserProfileResult.Found {
    return copy(posts = posts.map { if (it.likePostId == likePostId) it.copy(body = it.body.withLiked(liked)) else it })
}

/**
 * ブーストを変えた結果を、取得し直さずに反映する
 */
fun UserProfileResult.Found.withBoosted(reactionPostId: String, boosted: Boolean): UserProfileResult.Found {
    return copy(posts = posts.map { if (it.reactionPostId == reactionPostId) it.copy(body = it.body.withBoosted(boosted)) else it })
}

/**
 * 一覧より古い自分の投稿を固定したときは [withPinned] で並べ直せないので、取得し直して先頭に出す
 */
fun UserProfileResult.Found.needsReloadForPin(pinnableNoteId: String, pinned: Boolean): Boolean {
    val ownProfile = posts.any { it.pinnableNoteId != null }
    return pinned && ownProfile && posts.none { it.pinnableNoteId == pinnableNoteId }
}

/**
 * 固定を変えた結果を、取得し直さずに反映する。サーバーと同じく、固定した投稿を固定した新しい順で先頭に並べ、残りを投稿の新しい順に並べる
 */
fun UserProfileResult.Found.withPinned(pinnableNoteId: String, pinned: Boolean): UserProfileResult.Found {
    val target = posts.firstOrNull { it.pinnableNoteId == pinnableNoteId } ?: return this
    if (target.pinned == pinned) return this
    val others = posts.filterNot { it.pinnableNoteId == pinnableNoteId }
    val pinnedPosts = others.filter { it.pinned }
    val unpinnedPosts = others.filterNot { it.pinned }
    val updated = target.copy(pinned = pinned)
    return copy(
        posts = if (pinned) {
            listOf(updated) + pinnedPosts + unpinnedPosts
        } else {
            pinnedPosts + (unpinnedPosts + updated).sortedByDescending { runCatching { Instant.parse(it.body.publishedAt) }.getOrNull() }
        },
    )
}
