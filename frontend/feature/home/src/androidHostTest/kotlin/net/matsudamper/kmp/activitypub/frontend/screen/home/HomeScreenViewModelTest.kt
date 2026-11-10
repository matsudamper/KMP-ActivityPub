package net.matsudamper.kmp.activitypub.frontend.screen.home

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
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.screen.PostedAtRefreshTicker
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import okio.Buffer

@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenViewModelTest {
    private val arrivals = Channel<String>(Channel.UNLIMITED)
    private val subscriptionRejected = MutableStateFlow(false)
    private val columnSettingStore = FakeTimelineColumnSettingStore(listOf(TimelineColumnSetting.Home))
    private var followingGroupsResponse = """{"errors":[{"message":"購読の仕分け先は見ない"}]}"""

    @Test
    fun postsAreSplitIntoColumnsByFollowingGroup() = withViewModel { viewModel, engine ->
        columnSettingStore.columns.value = listOf(TimelineColumnSetting.Home, TimelineColumnSetting.FollowingGroup("1"), TimelineColumnSetting.All)
        followingGroupsResponse = """{"data":{"userInfo":{"__typename":"UserInfo","id":"me","followingGroups":[{"__typename":"FollowingGroup","id":"1","name":"ニュース"}]}}}"""
        viewModel.uiStateFlow.value.listener.onClickAddTimelineColumn()
        engine.responses.send {
            response("""{"data":{"userInfo":{"__typename":"UserInfo","homeTimeline":{"__typename":"HomeTimeline","posts":[${post("a")},${postInGroup("b", "1")}],"nextCursor":null}}}}""")
        }
        viewModel.uiStateFlow.value.listener.onScreenShown()

        val uiState = viewModel.uiStateFlow.first { it.timelineColumns.size == 3 && it.homeColumnItems().isNotEmpty() }
        assertEquals(listOf("ホーム", "ニュース", "全て"), uiState.timelineColumns.map { it.title })
        assertEquals(listOf("a"), uiState.timelineColumns[0].items.map { it.body.text.text })
        assertEquals(listOf("b"), uiState.timelineColumns[1].items.map { it.body.text.text })
        assertEquals(listOf("a", "b"), uiState.timelineColumns[2].items.map { it.body.text.text })
    }

    @Test
    fun movingColumnSavesNewOrder() = withViewModel { viewModel, _ ->
        columnSettingStore.columns.value = listOf(TimelineColumnSetting.Home, TimelineColumnSetting.FollowingGroup("1"), TimelineColumnSetting.All)
        followingGroupsResponse = """{"data":{"userInfo":{"__typename":"UserInfo","id":"me","followingGroups":[{"__typename":"FollowingGroup","id":"1","name":"ニュース"}]}}}"""
        viewModel.uiStateFlow.value.listener.onClickAddTimelineColumn()
        viewModel.uiStateFlow.first { it.timelineColumns.size == 3 }

        viewModel.uiStateFlow.value.listener.onMoveTimelineColumn(fromIndex = 0, toIndex = 2)
        val movedRight = viewModel.uiStateFlow.first { it.timelineColumns.firstOrNull()?.title == "ニュース" }
        assertEquals(listOf("ニュース", "全て", "ホーム"), movedRight.timelineColumns.map { it.title })

        viewModel.uiStateFlow.value.listener.onMoveTimelineColumn(fromIndex = 2, toIndex = 1)
        val movedLeft = viewModel.uiStateFlow.first { it.timelineColumns.getOrNull(1)?.title == "ホーム" }
        assertEquals(listOf("ニュース", "ホーム", "全て"), movedLeft.timelineColumns.map { it.title })
        assertEquals(
            listOf(TimelineColumnSetting.FollowingGroup("1"), TimelineColumnSetting.Home, TimelineColumnSetting.All),
            columnSettingStore.columns.value,
        )
    }

    @Test
    fun removingEveryColumnShowsEmptyMessageAndAddingBringsItBack() = withViewModel { viewModel, _ ->
        val home = viewModel.uiStateFlow.first { it.timelineColumns.isNotEmpty() }.timelineColumns.single()
        home.listener.onClickRemove()
        val emptied = viewModel.uiStateFlow.first { it.timelineColumns.isEmpty() }
        assertEquals(listOf(), columnSettingStore.columns.value)
        assertTrue(emptied.timelineColumnsEmptyMessage != null)
        assertTrue(emptied.addTimelineColumnButtonEnabled)

        emptied.listener.onClickAddTimelineColumn()
        val dialog = viewModel.uiStateFlow.first { it.addTimelineColumnDialog != null }.addTimelineColumnDialog
        assertEquals(listOf("全て", "ホーム"), dialog?.items?.map { it.name })
        dialog?.items?.first { it.name == "ホーム" }?.listener?.onClick()

        val restored = viewModel.uiStateFlow.first { it.timelineColumns.isNotEmpty() }
        assertEquals(listOf("ホーム"), restored.timelineColumns.map { it.title })
        assertEquals(null, restored.timelineColumnsEmptyMessage)
        assertEquals(null, restored.addTimelineColumnDialog)
        // 全てが残っているので、まだ足せる
        assertTrue(restored.addTimelineColumnButtonEnabled)
    }

    @Test
    fun arrivedPostsArePrependedAndKeptAcrossRefresh() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a"), null) }
        viewModel.uiStateFlow.value.listener.onScreenShown()
        viewModel.uiStateFlow.first { it.homeColumnItems().isNotEmpty() && !it.timelineRefreshing }
        arrivals.send("b")
        viewModel.uiStateFlow.first { it.homeColumnItems().size == 2 }
        assertEquals(listOf("b", "a"), viewModel.uiStateFlow.value.homeColumnItems().map { it.body.text.text })

        val pending = CompletableDeferred<HttpResponse>()
        engine.responses.send { pending.await() }
        viewModel.uiStateFlow.value.listener.onRefreshTimeline()
        runCurrent()
        engine.started.first { it == 2 }
        arrivals.send("c")
        viewModel.uiStateFlow.first { it.homeColumnItems().size == 3 }
        pending.complete(page(listOf("b", "a"), null))
        viewModel.uiStateFlow.first { !it.timelineRefreshing && it.homeColumnItems().size == 3 }
        assertEquals(listOf("c", "b", "a"), viewModel.uiStateFlow.value.homeColumnItems().map { it.body.text.text })
    }

    @Test
    fun appendHasItsOwnLoadingIndicatorAndDeduplicatesPageBoundary() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a", "b"), "next") }
        viewModel.uiStateFlow.value.listener.onRefreshTimeline()
        viewModel.uiStateFlow.first { it.homeColumnItems().isNotEmpty() && !it.timelineRefreshing }
        val pending = CompletableDeferred<HttpResponse>()
        engine.responses.send { pending.await() }
        viewModel.uiStateFlow.value.listener.onScrolledToTimelineEnd()
        runCurrent()
        engine.started.first { it == 2 }
        assertEquals(HomeScreenUiState.TimelineFooter.Loading, viewModel.uiStateFlow.value.timelineFooter)
        assertFalse(viewModel.uiStateFlow.value.timelineRefreshing)
        viewModel.uiStateFlow.value.listener.onScrolledToTimelineEnd()
        assertEquals(2, engine.requests)
        pending.complete(page(listOf("b", "c"), null))
        viewModel.uiStateFlow.first { it.homeColumnItems().size == 3 }
        assertEquals(listOf("a", "b", "c"), viewModel.uiStateFlow.value.homeColumnItems().map { it.body.text.text })
        viewModel.uiStateFlow.value.listener.onScrolledToTimelineEnd()
        advanceUntilIdle()
        assertEquals(2, engine.requests)
    }

    @Test
    fun appendFailureStopsAutomaticRetryUntilRetryButtonIsPressed() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a"), "next") }
        viewModel.uiStateFlow.value.listener.onRefreshTimeline()
        viewModel.uiStateFlow.first { it.homeColumnItems().isNotEmpty() && !it.timelineRefreshing }
        engine.responses.send { response("""{"errors":[{"message":"追加取得失敗"}]}""") }
        viewModel.uiStateFlow.value.listener.onScrolledToTimelineEnd()
        viewModel.uiStateFlow.first { it.timelineFooter is HomeScreenUiState.TimelineFooter.Error }
        assertTrue(viewModel.uiStateFlow.value.timelineFooter is HomeScreenUiState.TimelineFooter.Error)
        viewModel.uiStateFlow.value.listener.onScrolledToTimelineEnd()
        advanceUntilIdle()
        assertEquals(2, engine.requests)
        engine.responses.send { page(listOf("b"), null) }
        viewModel.uiStateFlow.value.listener.onClickRetryTimelineLoadMore()
        viewModel.uiStateFlow.first { it.homeColumnItems().size == 2 }
        assertEquals(listOf("a", "b"), viewModel.uiStateFlow.value.homeColumnItems().map { it.body.text.text })
        assertEquals(HomeScreenUiState.TimelineFooter.None, viewModel.uiStateFlow.value.timelineFooter)
    }

    @Test
    fun refreshCancelsAppendAndReplacesTheFirstPage() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a"), "next") }
        viewModel.uiStateFlow.value.listener.onRefreshTimeline()
        viewModel.uiStateFlow.first { it.homeColumnItems().isNotEmpty() && !it.timelineRefreshing }
        val pending = CompletableDeferred<HttpResponse>()
        val cancelled = CompletableDeferred<Unit>()
        engine.responses.send {
            try {
                pending.await()
            } finally {
                cancelled.complete(Unit)
            }
        }
        viewModel.uiStateFlow.value.listener.onScrolledToTimelineEnd()
        runCurrent()
        engine.started.first { it == 2 }
        engine.responses.send { page(listOf("fresh"), null) }
        viewModel.uiStateFlow.value.listener.onRefreshTimeline()
        viewModel.uiStateFlow.first { it.timelineRefreshing }
        assertEquals(HomeScreenUiState.TimelineFooter.None, viewModel.uiStateFlow.value.timelineFooter)
        viewModel.uiStateFlow.first { it.homeColumnItems().singleOrNull()?.body?.text?.text == "fresh" && !it.timelineRefreshing }
        assertTrue(cancelled.isCompleted)
        pending.complete(page(listOf("stale"), null))
        advanceUntilIdle()
        assertEquals(listOf("fresh"), viewModel.uiStateFlow.value.homeColumnItems().map { it.body.text.text })
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
        viewModel.uiStateFlow.first { it.homeColumnItems().size == 2 }
        arrivals.send("c")
        viewModel.uiStateFlow.first { it.homeColumnItems().size == 3 }
        assertEquals(listOf("c", "b", "a"), viewModel.uiStateFlow.value.homeColumnItems().map { it.body.text.text })
        assertEquals(HomeScreenUiState.TimelineStreamStatus.Receiving, viewModel.uiStateFlow.value.timelineStreamStatus)
    }

    @Test
    fun pressingStatusWhileReceivingStopsStreamUntilPressedAgain() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a"), null) }
        viewModel.uiStateFlow.value.listener.onScreenShown()
        viewModel.uiStateFlow.first { it.homeColumnItems().isNotEmpty() && !it.timelineRefreshing }
        viewModel.uiStateFlow.value.listener.onClickTimelineStreamStatus()
        viewModel.uiStateFlow.first { it.timelineStreamStatus == HomeScreenUiState.TimelineStreamStatus.Disconnected }

        // 画面に戻ってきても、自分で切った購読はつなぎ直さない
        engine.responses.send { page(listOf("a"), null) }
        viewModel.uiStateFlow.value.listener.onScreenShown()
        viewModel.uiStateFlow.first { !it.timelineRefreshing }
        arrivals.send("b")
        advanceUntilIdle()
        assertEquals(HomeScreenUiState.TimelineStreamStatus.Disconnected, viewModel.uiStateFlow.value.timelineStreamStatus)
        assertEquals(listOf("a"), viewModel.uiStateFlow.value.homeColumnItems().map { it.body.text.text })

        engine.responses.send { page(listOf("b", "a"), null) }
        viewModel.uiStateFlow.value.listener.onClickTimelineStreamStatus()
        viewModel.uiStateFlow.first { it.timelineStreamStatus == HomeScreenUiState.TimelineStreamStatus.Receiving }
        viewModel.uiStateFlow.first { it.homeColumnItems().size == 2 && !it.timelineRefreshing }
    }

    @Test
    fun pressingSelectedTabAgainScrollsToTopInsteadOfNavigating() = withViewModel { viewModel, _ ->
        val receivedEvents = Channel<String>(Channel.UNLIMITED)
        val collectJob = backgroundScope.launch {
            viewModel.eventHandler.collect(RecordingEvent(receivedEvents))
        }
        viewModel.uiStateFlow.value.listener.onClickTab(HomeScreenUiState.Tab.Home)
        assertEquals("scrollTabToTop:Home", receivedEvents.receive())
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

    private fun page(ids: List<String>, cursor: String?): HttpResponse {
        val posts = ids.joinToString(",") { id -> post(id) }
        val next = if (cursor == null) "null" else "\"${cursor}\""
        return response("""{"data":{"userInfo":{"__typename":"UserInfo","homeTimeline":{"__typename":"HomeTimeline","posts":[$posts],"nextCursor":$next}}}}""")
    }

    private fun post(id: String): String = postInGroup(id, null)

    private fun postInGroup(id: String, followingGroupId: String?): String {
        val groupId = if (followingGroupId == null) "null" else "\"$followingGroupId\""
        return """{"__typename":"HomeTimelinePost","id":"$id","content":"$id","publishedAt":"2026-11-01","username":"alice","host":null,"displayName":"Alice","displayNameEmojis":[],""" +
            """"summary":null,"sensitive":false,"linkUrls":[],"images":[],"emojis":[],"favouriteCount":null,"stamps":[],"url":null,"likedByMe":false,"followingGroupId":$groupId}"""
    }

    private fun HomeScreenUiState.homeColumnItems(): List<PostItemUiState> = timelineColumns.firstOrNull()?.items.orEmpty()

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
            return responses.receive().invoke()
        }

        override fun close() = Unit
    }

    private inner class ArrivalTransport : NetworkTransport {
        override fun <D : Operation.Data> execute(request: ApolloRequest<D>): Flow<ApolloResponse<D>> =
            if (subscriptionRejected.value) {
                emptyFlow()
            } else {
                arrivals.receiveAsFlow().map { id ->
                    request.operation.parseResponse(Buffer().writeUtf8("""{"data":{"homeTimelinePosts":${post(id)}}}""").jsonReader())
                }
            }

        override fun dispose() = Unit
    }
}
