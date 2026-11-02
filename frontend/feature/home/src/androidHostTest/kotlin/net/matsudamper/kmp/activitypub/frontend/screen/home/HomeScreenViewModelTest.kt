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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClientSource
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import okio.Buffer

@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenViewModelTest {
    @Test
    fun appendHasItsOwnLoadingIndicatorAndDeduplicatesPageBoundary() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a", "b"), "next") }
        viewModel.uiStateFlow.value.listener.onRefreshTimeline()
        viewModel.uiStateFlow.first { it.timelineItems.isNotEmpty() && !it.timelineRefreshing }
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
        viewModel.uiStateFlow.first { it.timelineItems.size == 3 }
        assertEquals(listOf("a", "b", "c"), viewModel.uiStateFlow.value.timelineItems.map { it.body })
        viewModel.uiStateFlow.value.listener.onScrolledToTimelineEnd()
        advanceUntilIdle()
        assertEquals(2, engine.requests)
    }

    @Test
    fun appendFailureStopsAutomaticRetryUntilRetryButtonIsPressed() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a"), "next") }
        viewModel.uiStateFlow.value.listener.onRefreshTimeline()
        viewModel.uiStateFlow.first { it.timelineItems.isNotEmpty() && !it.timelineRefreshing }
        engine.responses.send { response("""{"errors":[{"message":"追加取得失敗"}]}""") }
        viewModel.uiStateFlow.value.listener.onScrolledToTimelineEnd()
        viewModel.uiStateFlow.first { it.timelineFooter is HomeScreenUiState.TimelineFooter.Error }
        assertTrue(viewModel.uiStateFlow.value.timelineFooter is HomeScreenUiState.TimelineFooter.Error)
        viewModel.uiStateFlow.value.listener.onScrolledToTimelineEnd()
        advanceUntilIdle()
        assertEquals(2, engine.requests)
        engine.responses.send { page(listOf("b"), null) }
        viewModel.uiStateFlow.value.listener.onClickRetryTimelineLoadMore()
        viewModel.uiStateFlow.first { it.timelineItems.size == 2 }
        assertEquals(listOf("a", "b"), viewModel.uiStateFlow.value.timelineItems.map { it.body })
        assertEquals(HomeScreenUiState.TimelineFooter.None, viewModel.uiStateFlow.value.timelineFooter)
    }

    @Test
    fun refreshCancelsAppendAndReplacesTheFirstPage() = withViewModel { viewModel, engine ->
        engine.responses.send { page(listOf("a"), "next") }
        viewModel.uiStateFlow.value.listener.onRefreshTimeline()
        viewModel.uiStateFlow.first { it.timelineItems.isNotEmpty() && !it.timelineRefreshing }
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
        assertTrue(viewModel.uiStateFlow.value.timelineRefreshing)
        assertEquals(HomeScreenUiState.TimelineFooter.None, viewModel.uiStateFlow.value.timelineFooter)
        viewModel.uiStateFlow.first { it.timelineItems.singleOrNull()?.body == "fresh" && !it.timelineRefreshing }
        assertTrue(cancelled.isCompleted)
        pending.complete(page(listOf("stale"), null))
        advanceUntilIdle()
        assertEquals(listOf("fresh"), viewModel.uiStateFlow.value.timelineItems.map { it.body })
        assertFalse(viewModel.uiStateFlow.value.timelineRefreshing)
    }

    private fun withViewModel(block: suspend TestScope.(HomeScreenViewModel, QueueEngine) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = QueueEngine()
        val apolloClient = ApolloClient.Builder().serverUrl("https://example.com/graphql").httpEngine(engine).build()
        val source = object : GraphQlClientSource {
            override val client = MutableStateFlow<ApolloClient?>(apolloClient)
            override suspend fun awaitLoadedClient(): ApolloClient? = client.value
        }
        val viewModel = HomeScreenViewModel(HomeScreenUiState.Tab.Home, UserApi(source))
        try {
            block(viewModel, engine)
        } finally {
            viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            apolloClient.close()
            Dispatchers.resetMain()
        }
    }

    private fun page(ids: List<String>, cursor: String?): HttpResponse {
        val posts = ids.joinToString(",") { id ->
            """{"id":"$id","content":"$id","publishedAt":"2026-11-01","username":"alice","host":null,"displayName":"Alice","summary":null,"sensitive":false,"linkUrls":[]}"""
        }
        val next = if (cursor == null) "null" else "\"${cursor}\""
        return response("""{"data":{"userInfo":{"homeTimeline":{"posts":[$posts],"nextCursor":$next}}}}""")
    }

    private fun response(json: String): HttpResponse = HttpResponse.Builder(200).body(Buffer().writeUtf8(json)).build()

    private class QueueEngine : HttpEngine {
        val started = MutableStateFlow(0)
        val responses = Channel<suspend () -> HttpResponse>(Channel.UNLIMITED)
        val requests: Int get() = started.value

        override suspend fun execute(request: HttpRequest): HttpResponse {
            started.value++
            return responses.receive().invoke()
        }

        override fun close() = Unit
    }
}
