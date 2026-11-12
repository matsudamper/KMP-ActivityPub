package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.viewModelScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.api.json.jsonReader
import com.apollographql.apollo.api.parseResponse
import com.apollographql.apollo.network.NetworkTransport
import com.apollographql.apollo.network.http.HttpEngine
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClient
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClientSource
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineColumnSetting
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineColumnSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineSource
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.screen.PostedAtRefreshTicker
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import okio.Buffer

@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenViewModelTest {
    private val arrivals = Channel<String>(Channel.UNLIMITED)
    private val subscriptionRejected = MutableStateFlow(false)
    private val mainColumn = TimelineColumnSetting(id = "main", name = "メイン", sources = setOf(TimelineSource.MainGroup))
    private val newsColumn = TimelineColumnSetting(id = "news", name = "ニュース", sources = setOf(TimelineSource.FollowingGroup("1")))
    private val allColumn = TimelineColumnSetting(id = "all", name = "全部", sources = setOf(TimelineSource.MainGroup, TimelineSource.FollowingGroup("1")))
    private val columnSettingStore = FakeTimelineColumnSettingStore(listOf(mainColumn))
    private var followingGroupsResponse = """{"errors":[{"message":"購読の仕分け先は見ない"}]}"""

    @Test
    fun postsAreSplitIntoColumnsByCheckedSources() = withViewModel { viewModel, engine ->
        columnSettingStore.columns.value = listOf(mainColumn, newsColumn, allColumn)
        followingGroupsResponse = NEWS_GROUP_RESPONSE
        engine.responses.send { timelinePage(listOf(post(id = "a", followingGroupId = null, sortedAt = "2026-11-01T00:00:02Z")), null) }
        engine.groupResponses.send { timelinePage(listOf(post(id = "b", followingGroupId = "1", sortedAt = "2026-11-01T00:00:01Z")), null) }
        viewModel.uiStateFlow.value.listener.onScreenShown()

        val uiState = viewModel.uiStateFlow.first { it.timelineColumns.size == 3 && it.timelineColumns[2].items.size == 2 }
        assertEquals(listOf("メイン", "ニュース", "全部"), uiState.timelineColumns.map { it.title })
        assertEquals(listOf("a"), uiState.timelineColumns[0].items.map { it.body.text.text })
        assertEquals(listOf("b"), uiState.timelineColumns[1].items.map { it.body.text.text })
        assertEquals(listOf("a", "b"), uiState.timelineColumns[2].items.map { it.body.text.text })
    }

    @Test
    fun movingColumnSavesNewOrder() = withViewModel { viewModel, _ ->
        columnSettingStore.columns.value = listOf(mainColumn, newsColumn, allColumn)
        viewModel.uiStateFlow.first { it.timelineColumns.size == 3 }

        viewModel.uiStateFlow.value.listener.onMoveTimelineColumn(fromIndex = 0, toIndex = 2)
        val movedRight = viewModel.uiStateFlow.first { it.timelineColumns.firstOrNull()?.title == "ニュース" }
        assertEquals(listOf("ニュース", "全部", "メイン"), movedRight.timelineColumns.map { it.title })
        // 動かす前に見ていた先頭のメインを見続ける
        assertEquals("メイン", movedRight.timelineColumns.single { it.selected }.title)

        viewModel.uiStateFlow.value.listener.onMoveTimelineColumn(fromIndex = 2, toIndex = 1)
        val movedLeft = viewModel.uiStateFlow.first { it.timelineColumns.getOrNull(1)?.title == "メイン" }
        assertEquals(listOf("ニュース", "メイン", "全部"), movedLeft.timelineColumns.map { it.title })
        assertEquals(listOf(newsColumn, mainColumn, allColumn), columnSettingStore.columns.value)
    }

    @Test
    fun addingColumnSavesNameAndCheckedSources() = withViewModel { viewModel, _ ->
        followingGroupsResponse = NEWS_GROUP_RESPONSE
        val home = viewModel.uiStateFlow.first { it.timelineColumns.isNotEmpty() }.timelineColumns.single()
        home.listener.onClickDelete()
        val deleteDialog = viewModel.uiStateFlow.first { it.timelineColumnDeleteDialog != null }
        assertEquals(1, deleteDialog.timelineColumns.size)
        deleteDialog.timelineColumnDeleteDialog?.listener?.onClickDelete()
        val emptied = viewModel.uiStateFlow.first { it.timelineColumns.isEmpty() }
        assertEquals(listOf(), columnSettingStore.columns.value)
        assertTrue(emptied.timelineColumnsEmptyMessage != null)
        assertTrue(emptied.addTimelineColumnButtonEnabled)

        emptied.listener.onClickAddTimelineColumn()
        val dialog = viewModel.uiStateFlow.first { it.timelineColumnEditDialog?.sources?.size == 3 }.timelineColumnEditDialog
        assertEquals(listOf("メイン", "ニュース", "通知"), dialog?.sources?.map { it.name })
        assertEquals(ToggleableState.Off, dialog?.allSourcesCheckState)
        assertFalse(dialog?.saveButtonEnabled ?: true)

        dialog?.listener?.onNameChanged("まとめ")
        dialog?.listener?.onClickAllSources()
        val allChecked = viewModel.uiStateFlow.first { it.timelineColumnEditDialog?.allSourcesCheckState == ToggleableState.On }.timelineColumnEditDialog
        assertEquals(listOf(true, true, true), allChecked?.sources?.map { it.checked })
        allChecked?.sources?.first { it.name == "メイン" }?.listener?.onClick()
        val partlyChecked = viewModel.uiStateFlow.first { it.timelineColumnEditDialog?.allSourcesCheckState == ToggleableState.Indeterminate }
        assertTrue(partlyChecked.timelineColumnEditDialog?.saveButtonEnabled ?: false)
        partlyChecked.timelineColumnEditDialog?.listener?.onClickSave()

        val restored = viewModel.uiStateFlow.first { it.timelineColumns.isNotEmpty() }
        assertEquals(listOf("まとめ"), restored.timelineColumns.map { it.title })
        assertEquals(null, restored.timelineColumnsEmptyMessage)
        assertEquals(null, restored.timelineColumnEditDialog)
        val saved = columnSettingStore.columns.value.single()
        assertEquals("まとめ", saved.name)
        assertEquals(setOf(TimelineSource.FollowingGroup("1"), TimelineSource.Notifications), saved.sources)
    }

    @Test
    fun editingColumnKeepsIdAndUpdatesNameAndSources() = withViewModel { viewModel, _ ->
        followingGroupsResponse = NEWS_GROUP_RESPONSE
        columnSettingStore.columns.value = listOf(mainColumn, newsColumn)
        val news = viewModel.uiStateFlow.first { it.timelineColumns.size == 2 }.timelineColumns[1]

        news.listener.onClickEdit()
        val dialog = viewModel.uiStateFlow.first { it.timelineColumnEditDialog?.sources?.size == 3 }.timelineColumnEditDialog
        assertEquals("ニュース", dialog?.name)
        assertEquals(listOf(false, true, false), dialog?.sources?.map { it.checked })
        dialog?.listener?.onNameChanged("ニュースとメイン")
        dialog?.sources?.first { it.name == "メイン" }?.listener?.onClick()
        viewModel.uiStateFlow.first { uiState -> uiState.timelineColumnEditDialog?.sources?.map { it.checked } == listOf(true, true, false) }
            .timelineColumnEditDialog?.listener?.onClickSave()

        viewModel.uiStateFlow.first { it.timelineColumnEditDialog == null && it.timelineColumns[1].title == "ニュースとメイン" }
        assertEquals(
            listOf(mainColumn, newsColumn.copy(name = "ニュースとメイン", sources = setOf(TimelineSource.MainGroup, TimelineSource.FollowingGroup("1")))),
            columnSettingStore.columns.value,
        )
    }

    @Test
    fun arrivedPostsArePrependedAndKeptAcrossRefresh() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a"), null) }
        viewModel.uiStateFlow.value.listener.onScreenShown()
        viewModel.uiStateFlow.first { it.mainColumnItems().isNotEmpty() && !it.timelineRefreshing }
        arrivals.send(postArrived("b"))
        viewModel.uiStateFlow.first { it.mainColumnItems().size == 2 }
        assertEquals(listOf("b", "a"), viewModel.uiStateFlow.value.mainColumnItems().map { it.body.text.text })

        val pending = CompletableDeferred<HttpResponse>()
        engine.responses.send { pending.await() }
        viewModel.uiStateFlow.value.listener.onRefreshTimeline()
        runCurrent()
        engine.started.first { it == 2 }
        arrivals.send(postArrived("c"))
        viewModel.uiStateFlow.first { it.mainColumnItems().size == 3 }
        pending.complete(page(listOf("b", "a"), null))
        viewModel.uiStateFlow.first { !it.timelineRefreshing && it.mainColumnItems().size == 3 }
        assertEquals(listOf("c", "b", "a"), viewModel.uiStateFlow.value.mainColumnItems().map { it.body.text.text })
    }

    @Test
    fun appendHasItsOwnLoadingIndicatorAndDeduplicatesPageBoundary() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a", "b"), "next") }
        viewModel.uiStateFlow.value.listener.onRefreshTimeline()
        viewModel.uiStateFlow.first { it.mainColumnItems().isNotEmpty() && !it.timelineRefreshing }
        val pending = CompletableDeferred<HttpResponse>()
        engine.responses.send { pending.await() }
        viewModel.uiStateFlow.value.timelineColumns.first().listener.onScrolledToEnd()
        runCurrent()
        engine.started.first { it == 2 }
        assertEquals(HomeScreenUiState.TimelineFooter.Loading, viewModel.uiStateFlow.value.mainColumnFooter())
        assertFalse(viewModel.uiStateFlow.value.timelineRefreshing)
        viewModel.uiStateFlow.value.timelineColumns.first().listener.onScrolledToEnd()
        assertEquals(2, engine.requests)
        pending.complete(page(listOf("b", "c"), null))
        viewModel.uiStateFlow.first { it.mainColumnItems().size == 3 }
        assertEquals(listOf("a", "b", "c"), viewModel.uiStateFlow.value.mainColumnItems().map { it.body.text.text })
        viewModel.uiStateFlow.value.timelineColumns.first().listener.onScrolledToEnd()
        advanceUntilIdle()
        assertEquals(2, engine.requests)
    }

    @Test
    fun screenShownAgainKeepsLoadedPagesBelowFreshFirstPage() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a", "b"), "next") }
        viewModel.uiStateFlow.value.listener.onScreenShown()
        viewModel.uiStateFlow.first { it.mainColumnItems().size == 2 }
        // 読み込み中は続きを読まないので、読み終えるのを待つ
        advanceUntilIdle()
        engine.responses.send { page(listOf("c", "d"), null) }
        viewModel.uiStateFlow.value.timelineColumns.first().listener.onScrolledToEnd()
        viewModel.uiStateFlow.first { it.mainColumnItems().size == 4 }

        engine.responses.send { page(listOf("new", "a"), "next") }
        viewModel.uiStateFlow.value.listener.onScreenShown()
        viewModel.uiStateFlow.first { it.mainColumnItems().size == 5 }
        assertEquals(listOf("new", "a", "b", "c", "d"), viewModel.uiStateFlow.value.mainColumnItems().map { it.body.text.text })
    }

    @Test
    fun screenShownAgainReplacesLoadedPagesWhenFirstPageDoesNotReachThem() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a", "b"), "next") }
        viewModel.uiStateFlow.value.listener.onScreenShown()
        viewModel.uiStateFlow.first { it.mainColumnItems().size == 2 && !it.timelineRefreshing }

        engine.responses.send { page(listOf("x", "y"), "next") }
        viewModel.uiStateFlow.value.listener.onScreenShown()
        viewModel.uiStateFlow.first { it.mainColumnItems().firstOrNull()?.body?.text?.text == "x" }
        assertEquals(listOf("x", "y"), viewModel.uiStateFlow.value.mainColumnItems().map { it.body.text.text })
    }

    @Test
    fun appendFailureStopsAutomaticRetryUntilRetryButtonIsPressed() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a"), "next") }
        viewModel.uiStateFlow.value.listener.onRefreshTimeline()
        viewModel.uiStateFlow.first { it.mainColumnItems().isNotEmpty() && !it.timelineRefreshing }
        engine.responses.send { response("""{"errors":[{"message":"追加取得失敗"}]}""") }
        viewModel.uiStateFlow.value.timelineColumns.first().listener.onScrolledToEnd()
        viewModel.uiStateFlow.first { it.mainColumnFooter() is HomeScreenUiState.TimelineFooter.Error }
        assertTrue(viewModel.uiStateFlow.value.mainColumnFooter() is HomeScreenUiState.TimelineFooter.Error)
        viewModel.uiStateFlow.value.timelineColumns.first().listener.onScrolledToEnd()
        advanceUntilIdle()
        assertEquals(2, engine.requests)
        engine.responses.send { page(listOf("b"), null) }
        viewModel.uiStateFlow.value.timelineColumns.first().listener.onClickRetryLoadMore()
        viewModel.uiStateFlow.first { it.mainColumnItems().size == 2 }
        assertEquals(listOf("a", "b"), viewModel.uiStateFlow.value.mainColumnItems().map { it.body.text.text })
        assertEquals(HomeScreenUiState.TimelineFooter.None, viewModel.uiStateFlow.value.mainColumnFooter())
    }

    @Test
    fun refreshCancelsAppendAndReplacesTheFirstPage() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a"), "next") }
        viewModel.uiStateFlow.value.listener.onRefreshTimeline()
        viewModel.uiStateFlow.first { it.mainColumnItems().isNotEmpty() && !it.timelineRefreshing }
        val pending = CompletableDeferred<HttpResponse>()
        val cancelled = CompletableDeferred<Unit>()
        engine.responses.send {
            try {
                pending.await()
            } finally {
                cancelled.complete(Unit)
            }
        }
        viewModel.uiStateFlow.value.timelineColumns.first().listener.onScrolledToEnd()
        runCurrent()
        engine.started.first { it == 2 }
        engine.responses.send { page(listOf("fresh"), null) }
        viewModel.uiStateFlow.value.listener.onRefreshTimeline()
        viewModel.uiStateFlow.first { it.timelineRefreshing }
        assertEquals(HomeScreenUiState.TimelineFooter.None, viewModel.uiStateFlow.value.mainColumnFooter())
        viewModel.uiStateFlow.first { it.mainColumnItems().singleOrNull()?.body?.text?.text == "fresh" && !it.timelineRefreshing }
        assertTrue(cancelled.isCompleted)
        pending.complete(page(listOf("stale"), null))
        advanceUntilIdle()
        assertEquals(listOf("fresh"), viewModel.uiStateFlow.value.mainColumnItems().map { it.body.text.text })
        assertFalse(viewModel.uiStateFlow.value.timelineRefreshing)
    }

    @Test
    fun streamGivesUpAfterConsecutiveFailuresAndReconnectIconRestartsIt() = withViewModel { viewModel, engine ->
        subscriptionRejected.value = true
        engine.responses.send { page(listOf("a"), null) }
        viewModel.uiStateFlow.value.listener.onScreenShown()
        assertFalse(viewModel.uiStateFlow.value.timelineRefreshing)
        viewModel.uiStateFlow.first { it.timelineStreamStatus == HomeScreenUiState.TimelineStreamStatus.Disconnected }

        subscriptionRejected.value = false
        val pending = CompletableDeferred<HttpResponse>()
        engine.responses.send { pending.await() }
        viewModel.uiStateFlow.value.listener.onClickTimelineStreamStatus()
        runCurrent()
        // 再接続の読み直しは引っ張って更新したときの表示を出さず、アイコンで再接続中を見せる
        assertFalse(viewModel.uiStateFlow.value.timelineRefreshing)
        assertEquals(HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect, viewModel.uiStateFlow.value.timelineStreamStatus)
        pending.complete(page(listOf("b", "a"), null))
        viewModel.uiStateFlow.first { it.mainColumnItems().size == 2 }
        arrivals.send(postArrived("c"))
        viewModel.uiStateFlow.first { it.mainColumnItems().size == 3 }
        assertEquals(listOf("c", "b", "a"), viewModel.uiStateFlow.value.mainColumnItems().map { it.body.text.text })
        assertEquals(HomeScreenUiState.TimelineStreamStatus.Receiving, viewModel.uiStateFlow.value.timelineStreamStatus)
    }

    @Test
    fun pressingStatusWhileReceivingStopsStreamUntilPressedAgain() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a"), null) }
        viewModel.uiStateFlow.value.listener.onScreenShown()
        viewModel.uiStateFlow.first { it.mainColumnItems().isNotEmpty() && !it.timelineRefreshing }
        viewModel.uiStateFlow.value.listener.onClickTimelineStreamStatus()
        viewModel.uiStateFlow.first { it.timelineStreamStatus == HomeScreenUiState.TimelineStreamStatus.Disconnected }

        // 画面に戻ってきても、自分で切った購読はつなぎ直さない
        engine.responses.send { page(listOf("a"), null) }
        viewModel.uiStateFlow.value.listener.onScreenShown()
        viewModel.uiStateFlow.first { !it.timelineRefreshing }
        arrivals.send(postArrived("b"))
        advanceUntilIdle()
        assertEquals(HomeScreenUiState.TimelineStreamStatus.Disconnected, viewModel.uiStateFlow.value.timelineStreamStatus)
        assertEquals(listOf("a"), viewModel.uiStateFlow.value.mainColumnItems().map { it.body.text.text })

        engine.responses.send { page(listOf("b", "a"), null) }
        viewModel.uiStateFlow.value.listener.onClickTimelineStreamStatus()
        viewModel.uiStateFlow.first { it.timelineStreamStatus == HomeScreenUiState.TimelineStreamStatus.Receiving }
        viewModel.uiStateFlow.first { it.mainColumnItems().size == 2 && !it.timelineRefreshing }
    }

    @Test
    fun deletedPostKeepsItsContentWithDeletedLabelAcrossRefresh() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a", "b"), null) }
        viewModel.uiStateFlow.value.listener.onScreenShown()
        viewModel.uiStateFlow.first { it.mainColumnItems().size == 2 }
        assertEquals(listOf(false, false), viewModel.uiStateFlow.value.mainColumnItems().map { it.deletedLabelVisible })

        val pending = CompletableDeferred<HttpResponse>()
        engine.responses.send { pending.await() }
        viewModel.uiStateFlow.value.listener.onRefreshTimeline()
        runCurrent()
        engine.started.first { it == 2 }
        arrivals.send(postsDeleted(listOf("b", "not-loaded")))
        viewModel.uiStateFlow.first { uiState -> uiState.mainColumnItems().any { it.deletedLabelVisible } }
        // 読み直しの結果は削除より前のものなので、削除済みの印を引き継ぐ
        pending.complete(page(listOf("a", "b"), null))
        viewModel.uiStateFlow.first { !it.timelineRefreshing }
        advanceUntilIdle()

        val items = viewModel.uiStateFlow.value.mainColumnItems()
        assertEquals(listOf("a", "b"), items.map { it.body.text.text })
        assertEquals(listOf(false, true), items.map { it.deletedLabelVisible })
    }

    @Test
    fun notificationsColumnShowsLoadedAndArrivedNotificationsWithTheActor() = withViewModel { viewModel, engine ->
        columnSettingStore.columns.value = listOf(TimelineColumnSetting(id = "notifications", name = "通知", sources = setOf(TimelineSource.Notifications)))
        engine.notificationResponses.send {
            response(
                """{"data":{"userInfo":{"__typename":"UserInfo","notifications":{"__typename":"NotificationPage",""" +
                    """"notifications":[${notification(id = "1", kind = "FAVOURITE", actorName = "Bob", postId = "mine")}],"nextCursor":null}}}}""",
            )
        }
        viewModel.uiStateFlow.value.listener.onScreenShown()
        viewModel.uiStateFlow.first { it.mainColumnItems().size == 1 }
        arrivals.send(
            """{"__typename":"AccountEvent","timelinePostArrived":null,"timelinePostsDeleted":null,""" +
                """"notificationArrived":${notification(id = "2", kind = "BOOST", actorName = "Carol", postId = "mine")}}""",
        )

        val items = viewModel.uiStateFlow.first { it.mainColumnItems().size == 2 }.mainColumnItems()
        assertEquals(listOf("Carol がブースト", "Bob がお気に入り"), items.map { it.actorLabel?.text?.text?.text })
        assertEquals(listOf("mine", "mine"), items.map { it.body.text.text })
    }

    @Test
    fun mixedColumnHidesRowsOlderThanTheNewestUnreadTailUntilMoreIsLoaded() = withViewModel { viewModel, engine ->
        columnSettingStore.columns.value = listOf(allColumn)
        engine.responses.send {
            timelinePage(
                listOf(
                    post(id = "h3", followingGroupId = null, sortedAt = "2026-11-01T00:00:03Z"),
                    post(id = "h1", followingGroupId = null, sortedAt = "2026-11-01T00:00:01Z"),
                ),
                null,
            )
        }
        engine.groupResponses.send { timelinePage(listOf(post(id = "g2", followingGroupId = "1", sortedAt = "2026-11-01T00:00:02Z")), "next") }
        viewModel.uiStateFlow.value.listener.onScreenShown()

        // グループの続きに h1 より新しい投稿があるかもしれないので、h1 はまだ出さない
        viewModel.uiStateFlow.first { uiState -> uiState.mainColumnItems().map { it.body.text.text } == listOf("h3", "g2") }

        engine.groupResponses.send { timelinePage(listOf(post(id = "g0", followingGroupId = "1", sortedAt = "2026-11-01T00:00:00Z")), null) }
        viewModel.uiStateFlow.value.timelineColumns.first().listener.onScrolledToEnd()
        viewModel.uiStateFlow.first { it.mainColumnItems().size == 4 }
        assertEquals(listOf("h3", "g2", "h1", "g0"), viewModel.uiStateFlow.value.mainColumnItems().map { it.body.text.text })
        // 続きを読んだのはグループだけ
        assertEquals(3, engine.requests)
    }

    @Test
    fun pressingSelectedTabAgainScrollsToTopInsteadOfNavigating() = withViewModel { viewModel, _ ->
        val receivedEvents = Channel<String>(Channel.UNLIMITED)
        val collectJob = backgroundScope.launch {
            viewModel.eventHandler.collect(RecordingEvent(receivedEvents))
        }
        viewModel.uiStateFlow.first { uiState -> uiState.timelineColumns.any { it.selected } }
        viewModel.uiStateFlow.value.listener.onClickTab(HomeScreenUiState.Tab.Home)
        assertEquals("scrollTimelineColumnToTop:main", receivedEvents.receive())
        viewModel.uiStateFlow.value.listener.onClickTab(HomeScreenUiState.Tab.Search)
        assertEquals("navigateToTab:Search", receivedEvents.receive())
        collectJob.cancel()
    }

    private class RecordingEvent(
        private val receivedEvents: Channel<String>,
    ) : HomeScreenViewModel.Event {
        override fun navigateToTab(tab: HomeScreenUiState.Tab) {
            receivedEvents.trySend("navigateToTab:${tab.name}")
        }

        override fun scrollTabToTop(tab: HomeScreenUiState.Tab) {
            receivedEvents.trySend("scrollTabToTop:${tab.name}")
        }

        override fun scrollTimelineColumnToTop(listStateKey: String) {
            receivedEvents.trySend("scrollTimelineColumnToTop:$listStateKey")
        }

        override fun navigateToPost() = Unit

        override fun navigateToSettings() = Unit

        override fun navigateToUserProfile(username: String) = Unit

        override fun navigateToProfileEdit() = Unit

        override fun navigateToFollowing() = Unit

        override fun navigateToFollowers() = Unit

        override fun showMessage(message: String) = Unit

        override fun openUrl(url: String) = Unit

        override fun shareUrl(url: String) = Unit

        override fun navigateToImage(urls: List<String>, index: Int) = Unit

        override fun navigateToPostDetail(postId: String) = Unit
    }

    private fun withViewModel(block: suspend TestScope.(HomeScreenViewModel, QueueEngine) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = QueueEngine()
        val apolloClient = GraphQlClient.builder("https://example.com/graphql", ArrivalTransport()).httpEngine(engine).build()
        val source = object : GraphQlClientSource {
            override val client = MutableStateFlow<ApolloClient?>(apolloClient)
            override suspend fun awaitLoadedClient(): ApolloClient? = client.value
        }
        val viewModel = HomeScreenViewModel(
            HomeScreenUiState.Tab.Home,
            UserApi(source),
            object : PostedAtRefreshTicker {
                override val ticks: Flow<Unit> = emptyFlow()
            },
            columnSettingStore,
        )
        try {
            block(viewModel, engine)
        } finally {
            viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            apolloClient.close()
            Dispatchers.resetMain()
        }
    }

    private fun page(ids: List<String>, cursor: String?): HttpResponse =
        timelinePage(ids.map { id -> post(id) }, cursor)

    private fun timelinePage(posts: List<String>, cursor: String?): HttpResponse {
        val next = if (cursor == null) "null" else "\"${cursor}\""
        return response(
            """{"data":{"userInfo":{"__typename":"UserInfo","timeline":{"__typename":"TimelinePage","posts":[${posts.joinToString(",")}],"nextCursor":$next}}}}""",
        )
    }

    private fun post(id: String): String = post(id = id, followingGroupId = null, sortedAt = "2026-11-01T00:00:00Z")

    private fun post(id: String, followingGroupId: String?, sortedAt: String): String {
        val groupId = if (followingGroupId == null) "null" else "\"$followingGroupId\""
        return """{"__typename":"TimelinePost","id":"$id","deleted":false,"sortedAt":"$sortedAt","content":"$id","publishedAt":"2026-11-01",""" +
            """"author":${actor("Alice")},"summary":null,"sensitive":false,"linkUrls":[],"deletableNoteId":null,"pinnableNoteId":null,"pinned":false,""" +
            """"images":[],"emojis":[],"favouriteCount":null,"boostCount":null,"boostedBy":null,"stamps":[],"url":null,"likedByMe":false,""" +
            """"boostedByMe":null,"reactionPostId":"$id","followingGroupId":$groupId}"""
    }

    private fun actor(displayName: String): String =
        """{"__typename":"Actor","username":"${displayName.lowercase()}","host":"remote.example","displayName":"$displayName","displayNameEmojis":[],"iconUrl":null}"""

    private fun notification(id: String, kind: String, actorName: String, postId: String): String =
        """{"__typename":"Notification","id":"$id","kind":"$kind","actor":${actor(actorName)},"post":${post(postId)},"receivedAt":"2026-11-01T00:00:0${id}Z"}"""

    private fun postArrived(id: String): String =
        """{"__typename":"AccountEvent","timelinePostArrived":${post(id)},"timelinePostsDeleted":null,"notificationArrived":null}"""

    private fun postsDeleted(ids: List<String>): String {
        val postIds = ids.joinToString(",") { "\"$it\"" }
        return """{"__typename":"AccountEvent","timelinePostArrived":null,"timelinePostsDeleted":{"__typename":"TimelinePostsDeleted","postIds":[$postIds]},"notificationArrived":null}"""
    }

    private fun HomeScreenUiState.mainColumnItems(): List<PostItemUiState> = timelineColumns.firstOrNull()?.items.orEmpty()

    private fun HomeScreenUiState.mainColumnFooter(): HomeScreenUiState.TimelineFooter? = timelineColumns.firstOrNull()?.footer

    private class FakeTimelineColumnSettingStore(initialColumns: List<TimelineColumnSetting>) : TimelineColumnSettingStore {
        val columns = MutableStateFlow(initialColumns)

        override val columnsFlow: Flow<List<TimelineColumnSetting>> = columns

        override suspend fun save(columns: List<TimelineColumnSetting>): Boolean {
            this.columns.value = columns
            return true
        }
    }

    private fun response(json: String): HttpResponse = HttpResponse.Builder(200).body(Buffer().writeUtf8(json)).build()

    private inner class QueueEngine : HttpEngine {
        val started = MutableStateFlow(0)
        val responses = Channel<suspend () -> HttpResponse>(Channel.UNLIMITED)

        /**
         * グループ 1 のタイムラインの応答。購読先ごとに同時に読むので、メインの応答と取り合わないよう分ける
         */
        val groupResponses = Channel<suspend () -> HttpResponse>(Channel.UNLIMITED)
        val notificationResponses = Channel<suspend () -> HttpResponse>(Channel.UNLIMITED)
        val requests: Int get() = started.value

        override suspend fun execute(request: HttpRequest): HttpResponse {
            // タイムラインと同時に読むので、並べた応答を取り合わないよう別に返す
            val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
            if (body.contains("\"operationName\":\"MyProfile\"")) {
                return HttpResponse.Builder(200).body(Buffer().writeUtf8("""{"errors":[{"message":"プロフィールは見ない"}]}""")).build()
            }
            if (body.contains("\"operationName\":\"FollowingGroups\"")) {
                return HttpResponse.Builder(200).body(Buffer().writeUtf8(followingGroupsResponse)).build()
            }
            started.value++
            val queue = when {
                body.contains("\"operationName\":\"Notifications\"") -> notificationResponses
                body.contains("\"followingGroupId\":\"1\"") -> groupResponses
                else -> responses
            }
            return queue.receive().invoke()
        }

        override fun close() = Unit
    }

    private inner class ArrivalTransport : NetworkTransport {
        override fun <D : Operation.Data> execute(request: ApolloRequest<D>): Flow<ApolloResponse<D>> =
            if (subscriptionRejected.value) {
                emptyFlow()
            } else {
                arrivals.receiveAsFlow().map { event ->
                    request.operation.parseResponse(Buffer().writeUtf8("""{"data":{"accountEvents":$event}}""").jsonReader())
                }
            }

        override fun dispose() = Unit
    }
}

private const val NEWS_GROUP_RESPONSE = """{"data":{"userInfo":{"__typename":"UserInfo","id":"me","followingGroups":[{"__typename":"FollowingGroup","id":"1","name":"ニュース"}],"mainGroupName":"メイン"}}}"""
