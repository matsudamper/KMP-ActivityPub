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
