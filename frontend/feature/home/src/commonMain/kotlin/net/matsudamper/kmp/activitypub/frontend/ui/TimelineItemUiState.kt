package net.matsudamper.kmp.activitypub.frontend.ui

/**
 * タイムラインと通知のカラムに並べる 1 行
 */
sealed interface TimelineItemUiState {
    data class Post(val uiState: PostItemUiState) : TimelineItemUiState

    /**
     * 投稿の無い通知。フォローされたことを「〜にフォローされました」の行だけで出す
     */
    data class ActorOnly(val label: PostItemUiState.ActorLabel) : TimelineItemUiState
}

/**
 * 投稿の行と、投稿の無い通知の行とで、作り置きの中身を使い回さない
 */
internal val TimelineItemUiState.contentType: TimelineItemContentType
    get() = when (this) {
        is TimelineItemUiState.Post -> TimelineItemContentType.Post
        is TimelineItemUiState.ActorOnly -> TimelineItemContentType.ActorOnly
    }

internal enum class TimelineItemContentType {
    Post,
    ActorOnly,
}
