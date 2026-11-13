package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import net.matsudamper.kmp.activitypub.frontend.screen.theme.AppFontSize
import net.matsudamper.kmp.activitypub.frontend.ui.AddFollowingGroupDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.FollowMenuUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileLinkUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaUiState
import net.matsudamper.kmp.activitypub.frontend.ui.TextWithEmojis

data class HomeScreenUiState(
    val selectedTab: Tab,
    /**
     * 購読先ごとのカラム。大きい画面では横に並べ、小さい画面ではタブで切り替える
     */
    val timelineColumns: List<TimelineColumn>,
    /**
     * カラムを全部消していなければ null
     */
    val timelineColumnsEmptyMessage: String?,
    val addTimelineColumnButtonEnabled: Boolean,
    /**
     * 出していなければ null
     */
    val timelineColumnEditDialog: TimelineColumnEditDialog?,
    /**
     * 出していなければ null
     */
    val timelineColumnDeleteDialog: TimelineColumnDeleteDialog?,
    val timelineRefreshing: Boolean,
    val timelineStreamStatus: TimelineStreamStatus,
    val sensitiveImagesBlurred: Boolean,
    val notifications: Notifications,
    val search: Search,
    val profile: Profile,
    val userContentTab: ProfileContentTab,
    val userPosts: UserPosts,
    val userTabIconUrl: String?,
    /**
     * 横に広い画面でタイムラインの左に開くカラム。開いていなければ null
     */
    val openedColumn: Column?,
    val openedColumnWidth: Dp,
    val railItems: List<RailItem>,
    /**
     * 出していなければ null
     */
    val deleteNoteDialog: DeleteNoteDialogUiState?,
    /**
     * 出していなければ null
     */
    val addFollowingGroupDialog: AddFollowingGroupDialogUiState?,
    val listener: Listener,
) {
    enum class Column {
        Post,
        Notifications,
        Search,
        User,
    }

    data class RailItem(
        val column: Column,
        val selected: Boolean,
    )

    /**
     * @param listStateKey カラムを足し引きしても、それぞれのスクロール位置を取り違えないための鍵
     * @param footer 続きの読み込みの状態。カラムごとに流す購読先が違うので、カラムごとに持つ
     * @param selected 小さい画面で選んでいるタブ
     */
    data class TimelineColumn(
        val title: String,
        val listStateKey: String,
        val items: List<PostItemUiState>,
        val footer: TimelineFooter,
        val selected: Boolean,
        /**
         * 横に広い画面で並べるときの幅
         */
        val width: Dp,
        val fontSize: AppFontSize,
        val fontSizeMenuItems: List<FontSizeMenuItem>,
        val listener: TimelineColumnListener,
    )

    data class FontSizeMenuItem(
        val label: String,
        val checked: Boolean,
        val listener: FontSizeMenuItemListener,
    )

    @Immutable
    interface FontSizeMenuItemListener {
        fun onClick()
    }

    @Immutable
    interface TimelineColumnListener {
        fun onClickTab()

        fun onClickHeader()

        fun onClickEdit()

        fun onClickDelete()

        fun onScrolledToEnd()

        fun onClickRetryLoadMore()

        /**
         * 横に広い画面で、カラムの右の仕切りを動かしている間
         */
        fun onResize(delta: Dp)

        fun onResizeEnd()
    }

    /**
     * カラムの名前と、そこに流す購読先を決めるダイアログ。足すときも直すときも使う
     *
     * @param allSourcesCheckState 「すべて」のチェック。一部だけ選んでいれば [ToggleableState.Indeterminate]
     */
    data class TimelineColumnEditDialog(
        val title: String,
        val name: String,
        val allSourcesCheckState: ToggleableState,
        val sources: List<Source>,
        val saveButtonEnabled: Boolean,
        val listener: Listener,
    ) {
        data class Source(
            val name: String,
            val checked: Boolean,
            val listener: SourceListener,
        )

        @Immutable
        interface SourceListener {
            fun onClick()
        }

        @Immutable
        interface Listener {
            fun onNameChanged(name: String)

            fun onClickAllSources()

            fun onClickSave()

            fun onDismiss()
        }
    }

    /**
     * カラムを消す前に確かめるダイアログ
     */
    data class TimelineColumnDeleteDialog(
        val message: String,
        val listener: Listener,
    ) {
        @Immutable
        interface Listener {
            fun onClickDelete()

            fun onDismiss()
        }
    }

    sealed interface TimelineFooter {
        data object None : TimelineFooter
        data object Loading : TimelineFooter
        data class Error(val message: String) : TimelineFooter
    }

    enum class TimelineStreamStatus {
        Receiving,
        WaitingToReconnect,
        Disconnected,
    }

    enum class Tab {
        Home,
        Notifications,
        Search,
        User,
    }

    sealed interface Notifications {
        data object Loading : Notifications

        data class Error(
            val message: String,
        ) : Notifications

        /**
         * @param emptyMessage 通知があれば null
         */
        data class Loaded(
            val items: List<PostItemUiState>,
            val emptyMessage: String?,
            val refreshing: Boolean,
            val footer: TimelineFooter,
        ) : Notifications
    }

    data class Search(
        val query: String,
        val result: SearchResult,
    )

    sealed interface SearchResult {
        data object NotSearched : SearchResult

        data object Loading : SearchResult

        data class Loaded(
            val accounts: List<SearchAccount>,
            val loadMoreButtonVisible: Boolean,
        ) : SearchResult
    }

    /**
     * @param followMenu 購読できない相手（このサーバーのユーザー）には出さないので null
     */
    data class SearchAccount(
        val followMenu: FollowMenuUiState?,
        val iconUrl: String?,
        val displayName: TextWithEmojis,
        val acct: String,
        /**
         * 押しても何も起きないものは null
         */
        val listener: SearchAccountListener?,
    )

    sealed interface Profile {
        data object Loading : Profile

        data class Error(
            val message: String,
        ) : Profile

        data class Loaded(
            val iconUrl: String?,
            val displayName: String,
            val acct: String,
            val summary: String?,
            val links: List<ProfileLinkUiState>,
            val followingCount: String,
            val followersCount: String,
        ) : Profile
    }

    sealed interface UserPosts {
        data object Loading : UserPosts

        data class Loaded(
            val items: List<PostItemUiState>,
            val emptyMessage: String?,
            val media: List<ProfileMediaUiState>,
            val mediaEmptyMessage: String?,
        ) : UserPosts

        data object LoadFailed : UserPosts
    }

    @Immutable
    interface SearchAccountListener {
        fun onClick()
    }

    @Immutable
    interface Listener {
        fun onClickPost()

        fun onClickSettings()

        fun onClickTab(tab: Tab)

        fun onClickRailItem(column: Column)

        fun onClickCloseColumn(column: Column)

        fun onClickColumnHeader(tab: Tab)

        /**
         * [openedColumn] の右の仕切りを動かしている間
         */
        fun onResizeOpenedColumn(delta: Dp)

        fun onResizeOpenedColumnEnd()

        /**
         * 投稿カラムでの投稿や戻る操作が終わったとき。投稿カラムを閉じている間にも呼ばれる
         */
        fun onPostColumnFinished()

        fun onTabChanged(tab: Tab)

        fun onRefreshTimeline()

        fun onClickAddTimelineColumn()

        /**
         * タブやカラムの見出しを長押しして動かしたとき。どちらも [timelineColumns] での位置
         */
        fun onMoveTimelineColumn(fromIndex: Int, toIndex: Int)

        fun onClickTimelineStreamStatus()

        fun onClickSensitiveImagesVisibility()

        fun onRefreshNotifications()

        fun onClickRetryNotifications()

        fun onScrolledToNotificationsEnd()

        fun onClickRetryLoadMoreNotifications()

        fun onSearchQueryChanged(query: String)

        fun onSearch()

        fun onClickLoadMoreSearchResult()

        fun onClickEditProfile()

        fun onClickRetryProfile()

        fun onClickRetryUserPosts()

        fun onClickUserContentTab(tab: ProfileContentTab)

        fun onClickFollowing()

        fun onClickFollowers()

        fun onScreenShown()
    }
}
