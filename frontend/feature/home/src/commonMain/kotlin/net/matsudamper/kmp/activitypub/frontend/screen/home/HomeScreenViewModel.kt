package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.following.FollowMenuController
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeScreenSearchUsersQuery
import net.matsudamper.kmp.activitypub.frontend.logic.CursorPagingModel
import net.matsudamper.kmp.activitypub.frontend.logic.UpdateOperationResponseResult
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineColumnSetting
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineColumnSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineSource
import net.matsudamper.kmp.activitypub.frontend.logic.user.HomeTimelineStreamEvent
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserFollowingGroup
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLinkPreviewsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserMyProfileResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserProfileResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserResolveResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserSearchResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.needsReloadForPin
import net.matsudamper.kmp.activitypub.frontend.logic.user.toUserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.toUserSearchResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.withArrivedPosts
import net.matsudamper.kmp.activitypub.frontend.logic.user.withFirstPage
import net.matsudamper.kmp.activitypub.frontend.logic.user.withFollowing
import net.matsudamper.kmp.activitypub.frontend.logic.user.withLiked
import net.matsudamper.kmp.activitypub.frontend.logic.user.withPinned
import net.matsudamper.kmp.activitypub.frontend.logic.user.withoutNote
import net.matsudamper.kmp.activitypub.frontend.screen.AuthorTapAction
import net.matsudamper.kmp.activitypub.frontend.screen.Boost
import net.matsudamper.kmp.activitypub.frontend.screen.NoteDeletionState
import net.matsudamper.kmp.activitypub.frontend.screen.PostTapAction
import net.matsudamper.kmp.activitypub.frontend.screen.PostedAtRefreshTicker
import net.matsudamper.kmp.activitypub.frontend.screen.confirm
import net.matsudamper.kmp.activitypub.frontend.screen.createNoteMenuItems
import net.matsudamper.kmp.activitypub.frontend.screen.createPinMenuItems
import net.matsudamper.kmp.activitypub.frontend.screen.createPostItemUiState
import net.matsudamper.kmp.activitypub.frontend.screen.createProfileMedia
import net.matsudamper.kmp.activitypub.frontend.screen.createTextWithEmojis
import net.matsudamper.kmp.activitypub.frontend.screen.dismiss
import net.matsudamper.kmp.activitypub.frontend.screen.toDialogUiState
import net.matsudamper.kmp.activitypub.frontend.screen.withBlurToggled
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileLinkUiState
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class HomeScreenViewModel(
    @InjectedParam private val selectedTab: HomeScreenUiState.Tab,
    private val userApi: UserApi,
    private val postedAtRefreshTicker: PostedAtRefreshTicker,
    private val timelineColumnSettingStore: TimelineColumnSettingStore,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                selectedTab = selectedTab,
                openedColumn = null,
                timeline = TimelineState(
                    posts = listOf(),
                    nextCursor = null,
                    postsArrivedSinceRefresh = listOf(),
                    loading = TimelineLoading.None,
                    pullToRefreshIndicatorShowing = false,
                ),
                timelineStream = TimelineStreamState(
                    connection = TimelineStreamConnection.Receiving,
                    catchingUpAfterReconnect = false,
                ),
                linkPreviews = mapOf(),
                imageBlur = ImageBlurState(
                    sensitiveImagesBlurred = true,
                    toggledImageUrls = setOf(),
                ),
                search = SearchState(
                    query = "",
                    result = SearchResultState.NotSearched,
                ),
                profile = ProfileState.Loading,
                myPosts = MyPostsState.Loading,
                noteDeletion = NoteDeletionState.None,
                userContentTab = ProfileContentTab.Posts,
                timelineColumns = TimelineColumnsState(
                    settings = null,
                    homeName = userApi.currentHomeGroupName(),
                    followingGroups = listOf(),
                    selectedId = null,
                    editor = null,
                    deletingId = null,
                ),
            ),
        )
    private val timelinePaging = userApi.homeTimelinePaging()
    private var searchPaging: CursorPagingModel<HomeScreenSearchUsersQuery.Data>? = null
    private var searchJob: Job? = null
    private var searchWatchJob: Job? = null
    private var profileJob: Job? = null
    private var myPostsRetryJob: Job? = null
    private var timelineJob: Job? = null
    private var pullToRefreshIndicatorJob: Job? = null
    private var timelineArrivalsJob: Job? = null
    private val followMenuController = FollowMenuController(
        scope = viewModelScope,
        userApi = userApi,
        showMessage = { message -> events.send { it.showMessage(message) } },
    )

    /**
     * 表示には使わないので ViewModelState に入れない。入れると失敗時の削除で投稿の listener が作り直され、即座に取り直しを繰り返す
     */
    private var linkPreviewRequestedPostIds = setOf<String>()

    /**
     * 返事を待たずに表示を切り替えるので、押している間かどうかは表示に使わない。続けて押されたときに送り違えないよう、返事までは受け付けない
     */
    private var likeChangingPostIds = setOf<String>()

    /**
     * 続けて押されたときに送り違えないよう、返事までは受け付けない
     */
    private var pinChangingNoteIds = setOf<String>()

    private val listener = object : HomeScreenUiState.Listener {
        override fun onClickPost() {
            viewModelScope.launch {
                events.send { it.navigateToPost() }
            }
        }

        override fun onClickSettings() {
            viewModelScope.launch {
                events.send { it.navigateToSettings() }
            }
        }

        override fun onClickTab(tab: HomeScreenUiState.Tab) {
            val reselected = viewModelStateFlow.value.selectedTab == tab
            val selectedTimelineColumn = viewModelStateFlow.value.timelineColumns.selectedColumn()
            viewModelScope.launch {
                when {
                    !reselected -> events.send { it.navigateToTab(tab) }

                    // 縦並びでは選んでいるカラムのリストしか出ていない。出ていないリストはスクロールが終わらないので、選んでいるカラムだけ戻す
                    tab == HomeScreenUiState.Tab.Home -> {
                        if (selectedTimelineColumn != null) {
                            events.send { it.scrollTimelineColumnToTop(selectedTimelineColumn.id) }
                        }
                    }

                    else -> events.send { it.scrollTabToTop(tab) }
                }
            }
        }

        override fun onClickRailItem(column: HomeScreenUiState.Column) {
            viewModelStateFlow.update { it.copy(openedColumn = if (it.openedColumn == column) null else column) }
        }

        override fun onClickCloseColumn(column: HomeScreenUiState.Column) {
            viewModelStateFlow.update { if (it.openedColumn == column) it.copy(openedColumn = null) else it }
        }

        override fun onClickColumnHeader(tab: HomeScreenUiState.Tab) {
            viewModelScope.launch {
                events.send { it.scrollTabToTop(tab) }
            }
        }

        override fun onPostColumnFinished() {
            onClickCloseColumn(HomeScreenUiState.Column.Post)
            // 単独の投稿画面から戻るときはホームの再表示で読み直すが、カラムではホームが出たままなので、ここで同じものを読み直す。
            // 新着受信を止めている間は流れてこないので、タイムラインも読み直さないと自分の投稿が出ない
            loadProfile()
            loadTimeline(TimelineLoadTrigger.ScreenShown)
        }

        override fun onTabChanged(tab: HomeScreenUiState.Tab) {
            viewModelStateFlow.update { it.copy(selectedTab = tab) }
        }

        override fun onRefreshTimeline() {
            loadFollowingGroups()
            loadTimeline(TimelineLoadTrigger.PullToRefresh)
        }

        override fun onScrolledToTimelineEnd() {
            if (viewModelStateFlow.value.timeline.loading is TimelineLoading.AppendFailed) return
            loadTimeline(TimelineLoadTrigger.Append)
        }

        override fun onClickRetryTimelineLoadMore() {
            loadTimeline(TimelineLoadTrigger.Append)
        }

        override fun onClickTimelineStreamStatus() {
            when (viewModelStateFlow.value.timelineStream.status()) {
                HomeScreenUiState.TimelineStreamStatus.Receiving,
                HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect,
                -> stopTimelineArrivals()

                HomeScreenUiState.TimelineStreamStatus.Disconnected -> {
                    collectTimelineArrivals()
                    // 切れていた間の投稿は流れてこないので、読み直して埋める
                    loadTimeline(TimelineLoadTrigger.Reconnect)
                }
            }
        }

        override fun onClickSensitiveImagesVisibility() {
            viewModelStateFlow.update {
                it.copy(
                    imageBlur = ImageBlurState(
                        sensitiveImagesBlurred = !it.imageBlur.sensitiveImagesBlurred,
                        toggledImageUrls = setOf(),
                    ),
                )
            }
        }

        override fun onSearchQueryChanged(query: String) {
            updateSearch { it.copy(query = query) }
        }

        override fun onSearch() {
            search(query = viewModelStateFlow.value.search.query)
        }

        override fun onClickLoadMoreSearchResult() {
            loadMoreSearchResult()
        }

        override fun onClickEditProfile() {
            viewModelScope.launch {
                events.send { it.navigateToProfileEdit() }
            }
        }

        override fun onClickRetryProfile() {
            loadProfile()
        }

        override fun onClickRetryUserPosts() {
            retryMyPosts()
        }

        override fun onClickUserContentTab(tab: ProfileContentTab) {
            viewModelStateFlow.update { it.copy(userContentTab = tab) }
        }

        override fun onClickFollowing() {
            viewModelScope.launch {
                events.send { it.navigateToFollowing() }
            }
        }

        override fun onClickFollowers() {
            viewModelScope.launch {
                events.send { it.navigateToFollowers() }
            }
        }

        override fun onClickAddTimelineColumn() {
            if (viewModelStateFlow.value.timelineColumns.settings == null) return
            updateTimelineColumns { it.copy(editor = TimelineColumnEditorState(editingId = null, name = "", sources = setOf())) }
            // 別の画面でグループを作っていることがあるので、候補を出す前に読み直す
            loadFollowingGroups()
        }

        override fun onMoveTimelineColumn(fromIndex: Int, toIndex: Int) {
            moveTimelineColumn(fromIndex = fromIndex, toIndex = toIndex)
        }

        override fun onScreenShown() {
            loadProfile()
            // 読めなかったときは空になり、グループのカラムが消えたままになるので、戻るたびに読み直す
            loadFollowingGroups()
            // 読み込みより先に購読を始め、読み込み結果との間で投稿を取りこぼしにくくする
            if (viewModelStateFlow.value.timelineStream.connection != TimelineStreamConnection.StoppedByUser) {
                collectTimelineArrivals()
            }
            loadTimeline(TimelineLoadTrigger.ScreenShown)
        }
    }

    private val timelineColumnEditDialogListener = object : HomeScreenUiState.TimelineColumnEditDialog.Listener {
        override fun onNameChanged(name: String) {
            updateTimelineColumnEditor { it.copy(name = name) }
        }

        override fun onClickAllSources() {
            updateTimelineColumns { state ->
                val editor = state.editor ?: return@updateTimelineColumns state
                val selectableSources = state.selectableSources()
                val allChecked = editor.sources.containsAll(selectableSources)
                state.copy(editor = editor.copy(sources = if (allChecked) editor.sources - selectableSources.toSet() else editor.sources + selectableSources))
            }
        }

        override fun onClickSave() {
            saveEditingTimelineColumn()
        }

        override fun onDismiss() {
            updateTimelineColumns { it.copy(editor = null) }
        }
    }

    private val timelineColumnDeleteDialogListener = object : HomeScreenUiState.TimelineColumnDeleteDialog.Listener {
        override fun onClickDelete() {
            val state = viewModelStateFlow.value.timelineColumns
            val column = state.settings.orEmpty().firstOrNull { it.id == state.deletingId }
            updateTimelineColumns { it.copy(deletingId = null) }
            if (column != null) {
                removeTimelineColumn(column)
            }
        }

        override fun onDismiss() {
            updateTimelineColumns { it.copy(deletingId = null) }
        }
    }

    private val deleteNoteDialogListener = object : DeleteNoteDialogUiState.Listener {
        override fun onClickDelete() {
            deleteNote()
        }

        override fun onDismiss() {
            viewModelStateFlow.update { it.copy(noteDeletion = it.noteDeletion.dismiss()) }
        }
    }

    val uiStateFlow: StateFlow<HomeScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value, followMenuController.initialSnapshot)).also { uiStateFlow ->
            viewModelScope.launch {
                combine(
                    viewModelStateFlow,
                    followMenuController.snapshots,
                    postedAtRefreshTicker.ticks.onStart { emit(Unit) },
                ) { viewModelState, followMenu, _ ->
                    createUiState(viewModelState, followMenu)
                }.collect { uiState ->
                    uiStateFlow.value = uiState
                }
            }
        }.asStateFlow()

    init {
        viewModelScope.launch {
            userApi.deletedNoteIds.collect { noteId -> removeDeletedNote(noteId) }
        }
        viewModelScope.launch {
            // プロフィール画面や個別投稿画面で変えた固定もここで反映する
            userApi.pinChanges.collect { change ->
                timelinePaging.updateCache { it.withPinned(pinnableNoteId = change.noteId, pinned = change.pinned) }
                updateTimeline { timeline ->
                    timeline.copy(
                        postsArrivedSinceRefresh = timeline.postsArrivedSinceRefresh.map { post ->
                            if (post.pinnableNoteId == change.noteId) post.copy(pinned = change.pinned) else post
                        },
                    )
                }
                val myPosts = viewModelStateFlow.value.myPosts as? MyPostsState.Loaded
                if (myPosts != null && myPosts.profile.needsReloadForPin(pinnableNoteId = change.noteId, pinned = change.pinned)) {
                    loadMyPosts(myPosts.profile.username)
                } else {
                    viewModelStateFlow.update {
                        val loaded = it.myPosts as? MyPostsState.Loaded ?: return@update it
                        it.copy(myPosts = MyPostsState.Loaded(loaded.profile.withPinned(pinnableNoteId = change.noteId, pinned = change.pinned)))
                    }
                }
            }
        }
        viewModelScope.launch {
            userApi.followingChanges.collect { change ->
                // 購読の数が変わる
                loadProfile()
                // 読み込み済みの投稿は前の購読先のまま振り分けられるので、読み直して今の購読先に振り分ける
                loadTimeline(TimelineLoadTrigger.FollowingChanged)
                searchPaging?.updateCache { it.withFollowing(change) }
                updateSearchResult { result ->
                    if (result is SearchResultState.Loaded) {
                        result.copy(
                            users = result.users.map { user ->
                                if (user.actorUri == change.actorUri) user.copy(following = change.following, followingGroupId = change.groupId) else user
                            },
                        )
                    } else {
                        result
                    }
                }
            }
        }
        viewModelScope.launch {
            timelinePaging.watch().collect { applyTimeline(it.toUserHomeTimelineResult()) }
        }
        viewModelScope.launch {
            timelineColumnSettingStore.columnsFlow.collect { settings ->
                updateTimelineColumns { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            userApi.followingGroups.collect { groups ->
                updateTimelineColumns { it.copy(followingGroups = groups) }
            }
        }
        viewModelScope.launch {
            userApi.homeGroupName.collect { homeName ->
                updateTimelineColumns { it.copy(homeName = homeName) }
            }
        }
    }

    private fun loadFollowingGroups() {
        viewModelScope.launch { userApi.loadFollowingGroups() }
    }

    private fun updateTimelineColumns(transform: (TimelineColumnsState) -> TimelineColumnsState) {
        viewModelStateFlow.update { it.copy(timelineColumns = transform(it.timelineColumns)) }
    }

    private fun updateTimelineColumnEditor(transform: (TimelineColumnEditorState) -> TimelineColumnEditorState) {
        updateTimelineColumns { state ->
            val editor = state.editor
            if (editor == null) state else state.copy(editor = transform(editor))
        }
    }

    private fun editTimelineColumn(column: TimelineColumnSetting) {
        updateTimelineColumns {
            it.copy(editor = TimelineColumnEditorState(editingId = column.id, name = column.name, sources = column.sources))
        }
        // 別の画面でグループを作っていることがあるので、候補を出す前に読み直す
        loadFollowingGroups()
    }

    private fun saveEditingTimelineColumn() {
        val state = viewModelStateFlow.value.timelineColumns
        val settings = state.settings ?: return
        val editor = state.editor ?: return
        if (!state.editorSaveEnabled()) return
        val editingColumn = settings.firstOrNull { it.id == editor.editingId }
        val savedColumn = TimelineColumnSetting(
            id = editingColumn?.id ?: Uuid.random().toString(),
            name = editor.name.trim(),
            sources = editor.sources,
        )
        updateTimelineColumns { it.copy(selectedId = savedColumn.id, editor = null) }
        saveTimelineColumns(
            if (editingColumn == null) settings + savedColumn else settings.map { if (it.id == savedColumn.id) savedColumn else it },
        )
    }

    /**
     * 小さい画面で選んでいたタブを消したときは、隣のタブに移す
     */
    private fun removeTimelineColumn(column: TimelineColumnSetting) {
        val state = viewModelStateFlow.value.timelineColumns
        val settings = state.settings ?: return
        if (state.selectedColumn()?.id == column.id) {
            val removedIndex = settings.indexOfFirst { it.id == column.id }
            val remaining = settings.filterNot { it.id == column.id }
            updateTimelineColumns { it.copy(selectedId = remaining.getOrNull(removedIndex.coerceAtMost(remaining.lastIndex))?.id) }
        }
        saveTimelineColumns(settings.filterNot { it.id == column.id })
    }

    private fun moveTimelineColumn(fromIndex: Int, toIndex: Int) {
        val state = viewModelStateFlow.value.timelineColumns
        val settings = state.settings ?: return
        val movedColumn = settings.getOrNull(fromIndex) ?: return
        if (settings.getOrNull(toIndex) == null || fromIndex == toIndex) return
        // 選んでいないときは先頭を選んでいる扱いなので、並びが変わっても見ているタブが変わらないよう選んだことにする
        updateTimelineColumns { it.copy(selectedId = state.selectedColumn()?.id) }
        val withoutMoved = settings.filterIndexed { index, _ -> index != fromIndex }
        saveTimelineColumns(withoutMoved.take(toIndex) + movedColumn + withoutMoved.drop(toIndex))
    }

    /**
     * 端末への書き込みは後から届くので、続けて並べ替えたときに前の並べ替えを失わないよう、先に表示へ反映する
     */
    private fun saveTimelineColumns(settings: List<TimelineColumnSetting>) {
        updateTimelineColumns { it.copy(settings = settings) }
        viewModelScope.launch {
            if (!timelineColumnSettingStore.save(settings)) {
                events.send { it.showMessage(TIMELINE_COLUMNS_NOT_SAVED_MESSAGE) }
            }
        }
    }

    private fun loadProfile() {
        profileJob?.cancel()
        // 出している内容は読み直しの間も残す。戻るたびに読み込み中へ切り替わると画面がちらつく
        viewModelStateFlow.update {
            if (it.profile is ProfileState.Loaded) it else it.copy(profile = ProfileState.Loading)
        }
        profileJob = viewModelScope.launch {
            when (val result = userApi.myProfile()) {
                is UserMyProfileResult.Found -> {
                    viewModelStateFlow.update { it.copy(profile = ProfileState.Loaded(result)) }
                    loadMyPosts(result.username)
                }

                UserMyProfileResult.NotLoggedIn -> {
                    viewModelStateFlow.update { it.copy(profile = ProfileState.Failed(NOT_LOGGED_IN_MESSAGE)) }
                }

                is UserMyProfileResult.Failure -> {
                    viewModelStateFlow.update { it.copy(profile = ProfileState.Failed(result.message)) }
                }
            }
        }
    }

    private fun retryMyPosts() {
        val failed = viewModelStateFlow.value.myPosts as? MyPostsState.LoadFailed ?: return
        if (profileJob?.isActive == true || myPostsRetryJob?.isActive == true) return
        myPostsRetryJob = viewModelScope.launch { loadMyPosts(failed.username) }
    }

    private suspend fun loadMyPosts(username: String) {
        viewModelStateFlow.update {
            if (it.myPosts is MyPostsState.LoadFailed) it.copy(myPosts = MyPostsState.Loading) else it
        }
        when (val result = userApi.userProfile(username = username)) {
            is UserProfileResult.Found -> {
                viewModelStateFlow.update { it.copy(myPosts = MyPostsState.Loaded(result)) }
            }

            UserProfileResult.NotFound -> myPostsFailed(username, MY_POSTS_NOT_FOUND_MESSAGE)

            is UserProfileResult.Failure -> myPostsFailed(username, result.message)
        }
    }

    /**
     * 出している投稿は読み直しに失敗しても残す
     */
    private suspend fun myPostsFailed(username: String, message: String) {
        viewModelStateFlow.update {
            if (it.myPosts is MyPostsState.Loaded) it else it.copy(myPosts = MyPostsState.LoadFailed(username))
        }
        events.send { it.showMessage(message) }
    }

    private fun search(query: String) {
        val trimmedQuery = query.trim()
        searchJob?.cancel()
        searchWatchJob?.cancel()
        searchPaging = null
        if (trimmedQuery.isEmpty()) {
            updateSearchResult { SearchResultState.NotSearched }
            return
        }
        updateSearchResult { SearchResultState.Loading }
        if (isResolveQuery(trimmedQuery)) {
            searchJob = viewModelScope.launch { resolveUser(trimmedQuery) }
            return
        }
        val paging = userApi.searchUsersPaging(trimmedQuery)
        searchPaging = paging
        searchWatchJob = viewModelScope.launch {
            paging.watch().collect { applySearchResult(trimmedQuery, it.toUserSearchResult()) }
        }
        searchJob = viewModelScope.launch {
            when (val result = paging.refresh()) {
                is UpdateOperationResponseResult.Success -> {
                    val data = result.result.data
                    if (data != null) applySearchResult(trimmedQuery, data.toUserSearchResult())
                }

                is UpdateOperationResponseResult.Error -> {
                    searchWatchJob?.cancel()
                    updateSearchResult { SearchResultState.NotSearched }
                    events.send { it.showMessage(result.e?.message ?: NETWORK_ERROR_MESSAGE) }
                }

                is UpdateOperationResponseResult.NoHasMore -> Unit
            }
        }
    }

    private fun updateSearch(transform: (SearchState) -> SearchState) {
        viewModelStateFlow.update { it.copy(search = transform(it.search)) }
    }

    private fun updateSearchResult(transform: (SearchResultState) -> SearchResultState) {
        updateSearch { it.copy(result = transform(it.result)) }
    }

    private fun collectTimelineArrivals() {
        if (timelineArrivalsJob?.isActive == true) return
        timelineArrivalsJob = viewModelScope.launch {
            userApi.homeTimelineStream().collect { event ->
                when (event) {
                    HomeTimelineStreamEvent.Subscribing -> updateTimelineStreamConnection(TimelineStreamConnection.Receiving)

                    is HomeTimelineStreamEvent.Arrived -> {
                        updateTimeline { it.copy(postsArrivedSinceRefresh = listOf(event.post) + it.postsArrivedSinceRefresh) }
                        timelinePaging.updateCache { it.withArrivedPosts(listOf(event.post)) }
                    }

                    HomeTimelineStreamEvent.WaitingToReconnect -> {
                        updateTimelineStreamConnection(TimelineStreamConnection.WaitingToReconnect)
                    }

                    HomeTimelineStreamEvent.GaveUp -> {
                        updateTimelineStreamConnection(TimelineStreamConnection.GaveUp)
                        // 受け手が居ないと送り終わらず購読の Job が残り、再接続で始め直せなくなる
                        viewModelScope.launch { events.send { it.showMessage(TIMELINE_STREAM_DISCONNECTED_MESSAGE) } }
                    }
                }
            }
        }
    }

    private fun stopTimelineArrivals() {
        timelineArrivalsJob?.cancel()
        updateTimelineStreamConnection(TimelineStreamConnection.StoppedByUser)
    }

    private fun updateTimelineStreamConnection(connection: TimelineStreamConnection) {
        viewModelStateFlow.update { it.copy(timelineStream = it.timelineStream.copy(connection = connection)) }
    }

    private fun updateTimelineCatchingUpAfterReconnect(catchingUp: Boolean) {
        viewModelStateFlow.update {
            it.copy(timelineStream = it.timelineStream.copy(catchingUpAfterReconnect = catchingUp))
        }
    }

    private fun updateTimeline(transform: (TimelineState) -> TimelineState) {
        viewModelStateFlow.update { it.copy(timeline = transform(it.timeline)) }
    }

    /**
     * 同じ内容を書き込んだときは watch が流れないので、取得結果はここでも反映する
     */
    private fun applySearchResult(
        query: String,
        result: UserSearchResult.Success,
    ) {
        updateSearchResult {
            SearchResultState.Loaded(query = query, users = result.users, nextCursor = result.nextCursor)
        }
    }

    /**
     * `@user@host` と URL は名前の一部ではなく、1 人を指す書き方として引く
     */
    private fun isResolveQuery(query: String): Boolean =
        query.startsWith("https://") || query.startsWith("http://") || query.removePrefix("@").contains('@')

    private suspend fun resolveUser(query: String) {
        when (val result = userApi.resolveUser(query)) {
            is UserResolveResult.Found -> {
                updateSearchResult { SearchResultState.Loaded(query = query, users = listOf(result.user), nextCursor = null) }
            }

            UserResolveResult.NotFound -> {
                updateSearchResult { SearchResultState.Loaded(query = query, users = listOf(), nextCursor = null) }
            }

            is UserResolveResult.Failure -> {
                updateSearchResult { SearchResultState.NotSearched }
                events.send { it.showMessage(result.message) }
            }
        }
    }

    private fun loadMoreSearchResult() {
        if (searchJob?.isActive == true) return
        val paging = searchPaging ?: return
        val loaded = viewModelStateFlow.value.search.result as? SearchResultState.Loaded ?: return
        if (loaded.nextCursor == null) return
        searchJob = viewModelScope.launch {
            when (val result = paging.fetchNext()) {
                is UpdateOperationResponseResult.Success -> {
                    val data = result.result.data
                    if (data != null) applySearchResult(loaded.query, data.toUserSearchResult())
                }

                is UpdateOperationResponseResult.Error -> {
                    events.send { it.showMessage(result.e?.message ?: NETWORK_ERROR_MESSAGE) }
                }

                is UpdateOperationResponseResult.NoHasMore -> Unit
            }
        }
    }

    private fun loadTimeline(trigger: TimelineLoadTrigger) {
        val append = trigger == TimelineLoadTrigger.Append
        if (append && timelineJob?.isActive == true) return
        if (append && viewModelStateFlow.value.timeline.nextCursor == null) return
        timelineJob?.cancel()
        // 追加読み込みでは取り消さない。リフレッシュ表示の最低時間は取得の種類と関係なく守る
        if (!append) pullToRefreshIndicatorJob?.cancel()
        // 読み直しを別の読み直しで打ち切ったときに、再接続中の表示が残らないようにする
        if (!append) updateTimelineCatchingUpAfterReconnect(trigger == TimelineLoadTrigger.Reconnect)
        updateTimeline {
            it.copy(
                pullToRefreshIndicatorShowing = if (append) it.pullToRefreshIndicatorShowing else trigger == TimelineLoadTrigger.PullToRefresh,
                loading = when (trigger) {
                    TimelineLoadTrigger.Append -> TimelineLoading.Append

                    TimelineLoadTrigger.PullToRefresh,
                    TimelineLoadTrigger.ScreenShown,
                    TimelineLoadTrigger.FollowingChanged,
                    TimelineLoadTrigger.Reconnect,
                    -> TimelineLoading.None
                },
                postsArrivedSinceRefresh = if (append) it.postsArrivedSinceRefresh else listOf(),
            )
        }
        timelineJob = viewModelScope.launch {
            val startMark = TimeSource.Monotonic.markNow()
            try {
                val result = when (trigger) {
                    TimelineLoadTrigger.Append -> timelinePaging.fetchNext()

                    // 1 ページ目だけにすると、続きまで読んでいたカラムが戻ってきたときに位置を保てず、読み直しからやり直しになる
                    TimelineLoadTrigger.ScreenShown -> timelinePaging.refreshKeepingLoadedPages { loaded, firstPage -> loaded.withFirstPage(firstPage) }

                    TimelineLoadTrigger.PullToRefresh,
                    TimelineLoadTrigger.FollowingChanged,
                    TimelineLoadTrigger.Reconnect,
                    -> timelinePaging.refresh()
                }
                when (result) {
                    is UpdateOperationResponseResult.Success -> {
                        val timeline = result.result.data?.toUserHomeTimelineResult()
                        if (timeline != null) applyTimeline(timeline)
                        val postsArrivedSinceRefresh = viewModelStateFlow.value.timeline.postsArrivedSinceRefresh
                        if (!append && postsArrivedSinceRefresh.isNotEmpty()) {
                            timelinePaging.updateCache { it.withArrivedPosts(postsArrivedSinceRefresh) }
                        }
                        if (timeline == UserHomeTimelineResult.NotLoggedIn) {
                            timelineFailed(append, NOT_LOGGED_IN_MESSAGE)
                        }
                    }

                    is UpdateOperationResponseResult.Error -> timelineFailed(append, result.e?.message ?: NETWORK_ERROR_MESSAGE)

                    is UpdateOperationResponseResult.NoHasMore -> Unit
                }
            } finally {
                if (currentCoroutineContext().isActive) {
                    updateTimeline {
                        if (it.loading is TimelineLoading.AppendFailed) it else it.copy(loading = TimelineLoading.None)
                    }
                    if (trigger == TimelineLoadTrigger.PullToRefresh) keepPullToRefreshIndicator(startMark)
                    if (trigger == TimelineLoadTrigger.Reconnect) updateTimelineCatchingUpAfterReconnect(false)
                }
            }
        }
    }

    /**
     * 取得ジョブの中で待つと、その間の追加読み込みが取得中として破棄されるので、表示の維持は取得と別のジョブ・状態で行う
     */
    private fun keepPullToRefreshIndicator(startMark: TimeMark) {
        pullToRefreshIndicatorJob = viewModelScope.launch {
            val remaining = PULL_TO_REFRESH_MIN_DURATION - startMark.elapsedNow()
            if (remaining.isPositive()) delay(remaining)
            updateTimeline { it.copy(pullToRefreshIndicatorShowing = false) }
        }
    }

    /**
     * 同じ内容を書き込んだときは watch が流れないので、取得結果はここでも反映する
     */
    private fun applyTimeline(result: UserHomeTimelineResult) {
        val posts =
            when (result) {
                is UserHomeTimelineResult.Success -> result.posts
                UserHomeTimelineResult.NotLoggedIn -> listOf()
            }
        updateTimeline {
            it.copy(
                nextCursor = (result as? UserHomeTimelineResult.Success)?.nextCursor,
                // ページの境目で同じ投稿が両方のページに入ることがある
                posts = posts.distinctBy { post -> post.id },
            )
        }
    }

    private fun toggleImageBlur(imageUrls: List<String>, toggled: Boolean) {
        viewModelStateFlow.update {
            val urls = it.imageBlur.toggledImageUrls.withBlurToggled(imageUrls, toggled)
            it.copy(imageBlur = it.imageBlur.copy(toggledImageUrls = urls))
        }
    }

    private fun loadLinkPreviews(postId: String) {
        if (postId in linkPreviewRequestedPostIds) return
        linkPreviewRequestedPostIds = linkPreviewRequestedPostIds + postId
        viewModelScope.launch {
            when (val result = userApi.linkPreviews(postId)) {
                is UserLinkPreviewsResult.Success -> {
                    viewModelStateFlow.update { it.copy(linkPreviews = it.linkPreviews + (postId to result.previews)) }
                }

                // カードは URL だけでも出ているので、取れなくても知らせない。次に画面に入ったときに取り直す
                is UserLinkPreviewsResult.Failure,
                UserLinkPreviewsResult.NotLoggedIn,
                -> linkPreviewRequestedPostIds = linkPreviewRequestedPostIds - postId
            }
        }
    }

    /**
     * 自分の投稿はタイムラインと自分のタブの両方に出るので、両方を切り替える
     *
     * @param likedByMe 押す前の状態
     */
    private fun toggleLike(postId: String, likedByMe: Boolean) {
        if (postId in likeChangingPostIds) return
        likeChangingPostIds = likeChangingPostIds + postId
        viewModelScope.launch {
            try {
                applyLiked(postId = postId, liked = !likedByMe)
                val error = userApi.setLiked(postId = postId, liked = !likedByMe)
                if (error != null) {
                    applyLiked(postId = postId, liked = likedByMe)
                    events.send { it.showMessage(error) }
                }
            } finally {
                likeChangingPostIds = likeChangingPostIds - postId
            }
        }
    }

    private fun setPinned(pinnableNoteId: String, pinned: Boolean) {
        if (pinnableNoteId in pinChangingNoteIds) return
        pinChangingNoteIds = pinChangingNoteIds + pinnableNoteId
        viewModelScope.launch {
            try {
                val error = userApi.setPinned(noteId = pinnableNoteId, pinned = pinned)
                if (error != null) events.send { it.showMessage(error) }
            } finally {
                pinChangingNoteIds = pinChangingNoteIds - pinnableNoteId
            }
        }
    }

    private suspend fun applyLiked(postId: String, liked: Boolean) {
        timelinePaging.updateCache { it.withLiked(postId = postId, liked = liked) }
        viewModelStateFlow.update {
            val myPosts = it.myPosts as? MyPostsState.Loaded ?: return@update it
            it.copy(myPosts = MyPostsState.Loaded(myPosts.profile.withLiked(likePostId = postId, liked = liked)))
        }
    }

    private suspend fun timelineFailed(append: Boolean, message: String) {
        if (append) {
            updateTimeline { it.copy(loading = TimelineLoading.AppendFailed(message)) }
        } else {
            events.send { it.showMessage(message) }
        }
    }

    private fun deleteNote() {
        val confirming = viewModelStateFlow.value.noteDeletion as? NoteDeletionState.Confirming ?: return
        val noteId = confirming.noteId
        viewModelStateFlow.update { it.copy(noteDeletion = NoteDeletionState.Deleting(noteId)) }
        viewModelScope.launch {
            try {
                val error = userApi.deleteNote(noteId)
                if (error == null) {
                    events.send { it.showMessage("投稿を削除しました") }
                } else {
                    events.send { it.showMessage(error) }
                }
            } finally {
                viewModelStateFlow.update { it.copy(noteDeletion = NoteDeletionState.None) }
            }
        }
    }

    /**
     * 個別投稿画面など、ほかの画面で消した投稿もここで外す
     */
    private suspend fun removeDeletedNote(noteId: String) {
        timelinePaging.updateCache { it.withoutNote(noteId) }
        updateTimeline { timeline ->
            timeline.copy(postsArrivedSinceRefresh = timeline.postsArrivedSinceRefresh.filterNot { it.deletableNoteId == noteId })
        }
        viewModelStateFlow.update {
            val myPosts = it.myPosts as? MyPostsState.Loaded ?: return@update it
            val profile = myPosts.profile
            it.copy(myPosts = MyPostsState.Loaded(profile.copy(posts = profile.posts.filterNot { post -> post.deletableNoteId == noteId })))
        }
    }

    private fun createUiState(viewModelState: ViewModelState, followMenu: FollowMenuController.Snapshot): HomeScreenUiState {
        val profile = viewModelState.profile
        return HomeScreenUiState(
            selectedTab = viewModelState.selectedTab,
            timelineColumns = createTimelineColumns(viewModelState),
            timelineColumnsEmptyMessage = if (viewModelState.timelineColumns.settings?.isEmpty() == true) TIMELINE_COLUMNS_EMPTY_MESSAGE else null,
            addTimelineColumnButtonEnabled = viewModelState.timelineColumns.settings != null,
            timelineColumnEditDialog = createTimelineColumnEditDialog(viewModelState.timelineColumns),
            timelineColumnDeleteDialog = createTimelineColumnDeleteDialog(viewModelState.timelineColumns),
            timelineRefreshing = viewModelState.timeline.pullToRefreshIndicatorShowing,
            timelineFooter = createTimelineFooter(viewModelState.timeline.loading),
            timelineStreamStatus = viewModelState.timelineStream.status(),
            sensitiveImagesBlurred = viewModelState.imageBlur.sensitiveImagesBlurred,
            search = HomeScreenUiState.Search(
                query = viewModelState.search.query,
                result = createSearchResult(viewModelState.search, followMenu),
            ),
            profile = createProfile(profile),
            userContentTab = viewModelState.userContentTab,
            userPosts = createUserPosts(viewModelState),
            userTabIconUrl = (profile as? ProfileState.Loaded)?.profile?.iconUrl,
            openedColumn = viewModelState.openedColumn,
            railItems = HomeScreenUiState.Column.entries.map { column ->
                HomeScreenUiState.RailItem(column = column, selected = column == viewModelState.openedColumn)
            },
            deleteNoteDialog = viewModelState.noteDeletion.toDialogUiState(deleteNoteDialogListener),
            addFollowingGroupDialog = followMenuController.addGroupDialog(followMenu),
            listener = listener,
        )
    }

    private fun createTimelineFooter(loading: TimelineLoading): HomeScreenUiState.TimelineFooter {
        return when (loading) {
            is TimelineLoading.AppendFailed -> HomeScreenUiState.TimelineFooter.Error(loading.message)
            TimelineLoading.Append -> HomeScreenUiState.TimelineFooter.Loading
            TimelineLoading.None -> HomeScreenUiState.TimelineFooter.None
        }
    }

    private fun createProfile(profile: ProfileState): HomeScreenUiState.Profile {
        return when (profile) {
            ProfileState.Loading -> HomeScreenUiState.Profile.Loading

            is ProfileState.Loaded -> HomeScreenUiState.Profile.Loaded(
                iconUrl = profile.profile.iconUrl,
                displayName = profile.profile.displayName ?: profile.profile.username,
                acct = "@${profile.profile.username}",
                summary = profile.profile.summary,
                links = profile.profile.links.map { link ->
                    ProfileLinkUiState(
                        name = link.name,
                        url = link.url,
                        listener = object : ProfileLinkUiState.Listener {
                            override fun onClick() {
                                viewModelScope.launch { events.send { it.openUrl(link.url) } }
                            }
                        },
                    )
                },
                followingCount = profile.profile.followingCount.toString(),
                followersCount = profile.profile.followersCount.toString(),
            )

            is ProfileState.Failed -> HomeScreenUiState.Profile.Error(profile.message)
        }
    }

    private fun createUserPosts(viewModelState: ViewModelState): HomeScreenUiState.UserPosts {
        return when (val myPosts = viewModelState.myPosts) {
            MyPostsState.Loading -> HomeScreenUiState.UserPosts.Loading

            is MyPostsState.LoadFailed -> HomeScreenUiState.UserPosts.LoadFailed

            is MyPostsState.Loaded -> {
                val profile = myPosts.profile
                val media = createProfileMedia(profile.posts, ::navigateToPostDetail)
                HomeScreenUiState.UserPosts.Loaded(
                    items = profile.posts.map { post ->
                        val linkPreviewId = post.linkPreviewId
                        createPostItemUiState(
                            pinned = post.pinned,
                            boostedBy = null,
                            iconUrl = profile.iconUrl,
                            displayName = profile.displayName,
                            displayNameEmojis = profile.displayNameEmojis,
                            acct = "@${profile.username}",
                            body = post.body,
                            linkPreviews = linkPreviewId?.let { viewModelState.linkPreviews[it] }.orEmpty(),
                            sensitiveImagesBlurred = viewModelState.imageBlur.sensitiveImagesBlurred,
                            blurToggledImageUrls = viewModelState.imageBlur.toggledImageUrls,
                            authorTapAction = AuthorTapAction.AlreadyOnAuthorProfile,
                            menuItems = createPinMenuItems(post.pinnableNoteId, post.pinned, ::setPinned) + createNoteMenuItems(post.deletableNoteId, ::confirmNoteDeletion),
                            postTapAction = PostTapAction.OpenPostDetail(postId = post.postId, navigate = ::navigateToPostDetail),
                            onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
                            onOpenImage = { urls, index -> viewModelScope.launch { events.send { it.navigateToImage(urls, index) } } },
                            onToggleImageBlur = { imageUrls, toggled -> toggleImageBlur(imageUrls, toggled) },
                            onShare = { url -> viewModelScope.launch { events.send { it.shareUrl(url) } } },
                            onToggleLike = {
                                val likePostId = post.likePostId
                                val likedByMe = post.body.likedByMe
                                if (likePostId != null && likedByMe != null) toggleLike(postId = likePostId, likedByMe = likedByMe)
                            },
                            onVisible = {
                                if (linkPreviewId != null && post.body.linkUrls.isNotEmpty()) {
                                    loadLinkPreviews(linkPreviewId)
                                }
                            },
                        )
                    },
                    emptyMessage = if (profile.posts.isEmpty()) "表示できる投稿はありません" else null,
                    media = media,
                    mediaEmptyMessage = if (media.isEmpty()) "表示できるメディアはありません" else null,
                )
            }
        }
    }

    private fun navigateToPostDetail(postId: String) {
        viewModelScope.launch { events.send { it.navigateToPostDetail(postId) } }
    }

    private fun createTimelineColumns(viewModelState: ViewModelState): List<HomeScreenUiState.TimelineColumn> {
        val columnsState = viewModelState.timelineColumns
        val selectedColumn = columnsState.selectedColumn()
        return columnsState.settings.orEmpty().map { column ->
            HomeScreenUiState.TimelineColumn(
                title = column.name,
                listStateKey = column.id,
                items = createTimelineItems(viewModelState, column),
                selected = column.id == selectedColumn?.id,
                listener = object : HomeScreenUiState.TimelineColumnListener {
                    override fun onClickTab() {
                        if (viewModelStateFlow.value.timelineColumns.selectedColumn()?.id == column.id) {
                            viewModelScope.launch { events.send { it.scrollTimelineColumnToTop(column.id) } }
                        } else {
                            updateTimelineColumns { it.copy(selectedId = column.id) }
                        }
                    }

                    override fun onClickHeader() {
                        viewModelScope.launch { events.send { it.scrollTimelineColumnToTop(column.id) } }
                    }

                    override fun onClickEdit() {
                        editTimelineColumn(column)
                    }

                    override fun onClickDelete() {
                        updateTimelineColumns { it.copy(deletingId = column.id) }
                    }
                },
            )
        }
    }

    private fun createTimelineColumnDeleteDialog(columnsState: TimelineColumnsState): HomeScreenUiState.TimelineColumnDeleteDialog? {
        val column = columnsState.settings.orEmpty().firstOrNull { it.id == columnsState.deletingId } ?: return null
        return HomeScreenUiState.TimelineColumnDeleteDialog(
            message = "「${column.name}」のカラムを削除する",
            listener = timelineColumnDeleteDialogListener,
        )
    }

    private fun createTimelineColumnEditDialog(columnsState: TimelineColumnsState): HomeScreenUiState.TimelineColumnEditDialog? {
        val editor = columnsState.editor ?: return null
        val selectableSources = columnsState.selectableSources()
        val checkedCount = selectableSources.count { it in editor.sources }
        return HomeScreenUiState.TimelineColumnEditDialog(
            title = if (editor.editingId == null) ADD_TIMELINE_COLUMN_DIALOG_TITLE else EDIT_TIMELINE_COLUMN_DIALOG_TITLE,
            name = editor.name,
            allSourcesCheckState = when (checkedCount) {
                0 -> ToggleableState.Off
                selectableSources.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            },
            sources = selectableSources.map { source ->
                HomeScreenUiState.TimelineColumnEditDialog.Source(
                    name = columnsState.nameOf(source),
                    checked = source in editor.sources,
                    listener = object : HomeScreenUiState.TimelineColumnEditDialog.SourceListener {
                        override fun onClick() {
                            updateTimelineColumnEditor { it.copy(sources = if (source in it.sources) it.sources - source else it.sources + source) }
                        }
                    },
                )
            },
            saveButtonEnabled = columnsState.editorSaveEnabled(),
            listener = timelineColumnEditDialogListener,
        )
    }

    private fun createTimelineItems(viewModelState: ViewModelState, column: TimelineColumnSetting): List<PostItemUiState> {
        return viewModelState.timeline.posts.filter { column.contains(it) }.map { post ->
            createPostItemUiState(
                pinned = false,
                boostedBy = post.boostedBy?.let { booster ->
                    Boost(booster = booster) { username ->
                        viewModelScope.launch { events.send { it.navigateToUserProfile(username) } }
                    }
                },
                iconUrl = post.iconUrl,
                displayName = post.displayName,
                displayNameEmojis = post.displayNameEmojis,
                acct = if (post.host == null) "@${post.username}" else "@${post.username}@${post.host}",
                body = post.body,
                linkPreviews = viewModelState.linkPreviews[post.id].orEmpty(),
                sensitiveImagesBlurred = viewModelState.imageBlur.sensitiveImagesBlurred,
                blurToggledImageUrls = viewModelState.imageBlur.toggledImageUrls,
                authorTapAction = AuthorTapAction.OpenProfile {
                    val host = post.host
                    viewModelScope.launch {
                        events.send { it.navigateToUserProfile(if (host == null) post.username else "${post.username}@$host") }
                    }
                },
                menuItems = createPinMenuItems(post.pinnableNoteId, post.pinned, ::setPinned) + createNoteMenuItems(post.deletableNoteId, ::confirmNoteDeletion),
                postTapAction = PostTapAction.OpenPostDetail(postId = post.id, navigate = ::navigateToPostDetail),
                onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
                onOpenImage = { urls, index -> viewModelScope.launch { events.send { it.navigateToImage(urls, index) } } },
                onToggleImageBlur = { imageUrls, toggled -> toggleImageBlur(imageUrls, toggled) },
                onShare = { url -> viewModelScope.launch { events.send { it.shareUrl(url) } } },
                onToggleLike = {
                    val likedByMe = post.body.likedByMe
                    if (likedByMe != null) toggleLike(postId = post.id, likedByMe = likedByMe)
                },
                onVisible = {
                    if (post.body.linkUrls.isNotEmpty()) {
                        loadLinkPreviews(post.id)
                    }
                },
            )
        }
    }

    private fun confirmNoteDeletion(noteId: String) {
        viewModelStateFlow.update { it.copy(noteDeletion = it.noteDeletion.confirm(noteId)) }
    }

    private fun createSearchResult(search: SearchState, followMenu: FollowMenuController.Snapshot): HomeScreenUiState.SearchResult {
        return when (val result = search.result) {
            SearchResultState.NotSearched -> HomeScreenUiState.SearchResult.NotSearched

            SearchResultState.Loading -> HomeScreenUiState.SearchResult.Loading

            is SearchResultState.Loaded -> HomeScreenUiState.SearchResult.Loaded(
                accounts = result.users.map { user ->
                    createSearchAccount(user, followMenu)
                },
                loadMoreButtonVisible = result.nextCursor != null,
            )
        }
    }

    private fun createSearchAccount(
        user: UserSearchResult.User,
        followMenu: FollowMenuController.Snapshot,
    ): HomeScreenUiState.SearchAccount {
        return HomeScreenUiState.SearchAccount(
            followMenu = user.actorUri?.let { actorUri ->
                followMenuController.menu(
                    snapshot = followMenu,
                    actorUri = actorUri,
                    receivedFollowing = user.following,
                    receivedGroupId = user.followingGroupId,
                )
            },
            iconUrl = user.iconUrl,
            displayName = createTextWithEmojis(text = user.displayName, emojis = user.displayNameEmojis),
            acct = if (user.isLocal) "@${user.username}" else "@${user.username}@${user.host}",
            listener =
            object : HomeScreenUiState.SearchAccountListener {
                override fun onClick() {
                    viewModelScope.launch {
                        events.send { it.navigateToUserProfile(if (user.isLocal) user.username else "${user.username}@${user.host}") }
                    }
                }
            },
        )
    }

    /**
     * 再接続直後は購読がつながっていても切れていた間の投稿を読み直し終えるまでは追いついていないので、再接続中として見せる
     */
    private fun TimelineStreamState.status(): HomeScreenUiState.TimelineStreamStatus {
        return when (connection) {
            TimelineStreamConnection.Receiving -> if (catchingUpAfterReconnect) {
                HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect
            } else {
                HomeScreenUiState.TimelineStreamStatus.Receiving
            }

            TimelineStreamConnection.WaitingToReconnect -> HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect

            TimelineStreamConnection.GaveUp,
            TimelineStreamConnection.StoppedByUser,
            -> HomeScreenUiState.TimelineStreamStatus.Disconnected
        }
    }

    internal interface Event {
        fun navigateToTab(tab: HomeScreenUiState.Tab)

        fun scrollTabToTop(tab: HomeScreenUiState.Tab)

        fun scrollTimelineColumnToTop(listStateKey: String)

        fun navigateToPost()

        fun navigateToSettings()

        fun navigateToUserProfile(username: String)

        fun navigateToProfileEdit()

        fun navigateToFollowing()

        fun navigateToFollowers()

        fun showMessage(message: String)

        fun openUrl(url: String)

        fun shareUrl(url: String)

        fun navigateToImage(urls: List<String>, index: Int)

        fun navigateToPostDetail(postId: String)
    }

    private companion object {
        val PULL_TO_REFRESH_MIN_DURATION = 600.milliseconds
        const val NOT_LOGGED_IN_MESSAGE = "ログインしていません"
        const val NETWORK_ERROR_MESSAGE = "ネットワークエラー"
        const val MY_POSTS_NOT_FOUND_MESSAGE = "投稿を読み込めませんでした"
        const val TIMELINE_STREAM_DISCONNECTED_MESSAGE = "新着投稿の受信が切れました"
        const val TIMELINE_COLUMNS_EMPTY_MESSAGE = "表示するカラムがありません"
        const val ADD_TIMELINE_COLUMN_DIALOG_TITLE = "カラムを追加"
        const val EDIT_TIMELINE_COLUMN_DIALOG_TITLE = "カラムを編集"
        const val TIMELINE_COLUMNS_NOT_SAVED_MESSAGE = "カラムの設定を端末に保存できませんでした"
    }

    private enum class TimelineLoadTrigger {
        PullToRefresh,
        ScreenShown,
        FollowingChanged,
        Reconnect,
        Append,
    }

    private data class ViewModelState(
        val selectedTab: HomeScreenUiState.Tab,
        /**
         * 横に広い画面でタイムラインの左に開くカラム。開いていなければ null
         */
        val openedColumn: HomeScreenUiState.Column?,
        val timeline: TimelineState,
        val timelineStream: TimelineStreamState,
        val linkPreviews: Map<String, List<UserLinkPreviewsResult.LinkPreview>>,
        val imageBlur: ImageBlurState,
        val search: SearchState,
        val profile: ProfileState,
        val myPosts: MyPostsState,
        val noteDeletion: NoteDeletionState,
        val userContentTab: ProfileContentTab,
        val timelineColumns: TimelineColumnsState,
    )

    /**
     * @param settings 端末から読み終えるまでは null
     * @param followingGroups 読めていないグループは名前を出せないので、編集のダイアログに並べない
     * @param selectedId 小さい画面で選んでいるタブ。消えていれば先頭を選んでいるものとして扱う
     * @param editor 編集のダイアログを出していなければ null
     * @param deletingId 削除を確かめるダイアログを出していなければ null
     */
    private data class TimelineColumnsState(
        val settings: List<TimelineColumnSetting>?,
        val homeName: String,
        val followingGroups: List<UserFollowingGroup>,
        val selectedId: String?,
        val editor: TimelineColumnEditorState?,
        val deletingId: String?,
    ) {
        fun selectedColumn(): TimelineColumnSetting? {
            val columns = settings.orEmpty()
            return columns.firstOrNull { it.id == selectedId } ?: columns.firstOrNull()
        }

        fun selectableSources(): List<TimelineSource> =
            listOf(TimelineSource.Home) + followingGroups.map { TimelineSource.FollowingGroup(it.id) }

        fun nameOf(source: TimelineSource): String =
            when (source) {
                TimelineSource.Home -> homeName
                is TimelineSource.FollowingGroup -> followingGroups.firstOrNull { it.id == source.groupId }?.name.orEmpty()
            }

        /**
         * 端末から読み終えるまでは、保存した結果で読み終えた内容を上書きしないよう保存できない
         */
        fun editorSaveEnabled(): Boolean {
            val currentEditor = editor ?: return false
            return settings != null && currentEditor.name.isNotBlank() && currentEditor.sources.isNotEmpty()
        }
    }

    /**
     * @param editingId 新しく足すときは null
     * @param sources 名前を読めていないグループはチェックを出さないが、外したことにはせずここに残す
     */
    private data class TimelineColumnEditorState(
        val editingId: String?,
        val name: String,
        val sources: Set<TimelineSource>,
    )

    /**
     * @param postsArrivedSinceRefresh 読み直しの結果は要求した時点のものなので、その間に流れてきた投稿を先頭に足し直す
     */
    private data class TimelineState(
        val posts: List<UserHomeTimelineResult.Post>,
        val nextCursor: String?,
        val postsArrivedSinceRefresh: List<UserHomeTimelineResult.Post>,
        val loading: TimelineLoading,
        val pullToRefreshIndicatorShowing: Boolean,
    )

    /**
     * 画面に出ない読み直し（画面表示時・再接続時）は None
     */
    private sealed interface TimelineLoading {
        data object None : TimelineLoading

        data object Append : TimelineLoading

        /**
         * 失敗した後に自動で読み直すと、末尾にいる間は失敗を繰り返すので、ボタンを押すまで続きを読まない
         */
        data class AppendFailed(
            val message: String,
        ) : TimelineLoading
    }

    private data class TimelineStreamState(
        val connection: TimelineStreamConnection,
        val catchingUpAfterReconnect: Boolean,
    )

    private enum class TimelineStreamConnection {
        Receiving,
        WaitingToReconnect,
        GaveUp,

        /**
         * 自分で切ったときは、画面に戻ってきてもつなぎ直さない
         */
        StoppedByUser,
    }

    /**
     * @param toggledImageUrls 一括で切り替えたら、画像ごとに開け閉めした分は捨てて揃える
     */
    private data class ImageBlurState(
        val sensitiveImagesBlurred: Boolean,
        val toggledImageUrls: Set<String>,
    )

    private data class SearchState(
        val query: String,
        val result: SearchResultState,
    )

    private sealed interface SearchResultState {
        data object NotSearched : SearchResultState

        data object Loading : SearchResultState

        data class Loaded(
            val query: String,
            val users: List<UserSearchResult.User>,
            val nextCursor: String?,
        ) : SearchResultState
    }

    private sealed interface ProfileState {
        data object Loading : ProfileState

        data class Loaded(
            val profile: UserMyProfileResult.Found,
        ) : ProfileState

        data class Failed(
            val message: String,
        ) : ProfileState
    }

    private sealed interface MyPostsState {
        data object Loading : MyPostsState

        data class Loaded(
            val profile: UserProfileResult.Found,
        ) : MyPostsState

        /**
         * @param username 再試行で読み直す相手
         */
        data class LoadFailed(
            val username: String,
        ) : MyPostsState
    }
}

private fun TimelineColumnSetting.contains(post: UserHomeTimelineResult.Post): Boolean {
    val groupId = post.followingGroupId
    return if (groupId == null) TimelineSource.Home in sources else TimelineSource.FollowingGroup(groupId) in sources
}
