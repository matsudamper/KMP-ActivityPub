package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.logic.user.UserProfileResult
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaUiState

/**
 * プロフィールの投稿に付いた画像を、投稿の順・付いた順に並べる
 */
internal fun createProfileMedia(
    posts: List<UserProfileResult.Post>,
    sensitiveImagesBlurred: Boolean,
    onOpenPost: (postId: String) -> Unit,
): List<ProfileMediaUiState> {
    return posts.flatMap { post ->
        val listener = object : ProfileMediaUiState.Listener {
            override fun onClick() {
                onOpenPost(post.postId)
            }
        }
        post.body.images.map { image ->
            ProfileMediaUiState(
                url = image.thumbnailUrl,
                description = image.description,
                blurred = post.body.sensitive && sensitiveImagesBlurred,
                listener = listener,
            )
        }
    }
}

/**
 * メディアのタブに出る画像が 1 枚でもあるか
 */
internal fun List<UserProfileResult.Post>.containsProfileMedia(): Boolean =
    any { it.boostedNoteAuthor == null && it.body.images.isNotEmpty() }

/**
 * 画像の少ない相手では、画像の無いページを末尾の見張りで読み続けると最後のページまで止まらず、画像の取得の接続も塞ぐ。
 * この数だけ画像の無いページが続いたら、自動では読まずにボタンで読ませる
 */
internal const val MEDIA_AUTO_LOAD_PAGE_LIMIT_WITHOUT_MEDIA = 3
