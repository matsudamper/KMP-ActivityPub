package net.matsudamper.kmp.activitypub.frontend.screen.home

import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.apollographql.apollo.api.Query
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.TimelinePostFields
import net.matsudamper.kmp.activitypub.frontend.logic.CursorPagingModel
import net.matsudamper.kmp.activitypub.frontend.logic.UpdateOperationResponseResult
import net.matsudamper.kmp.activitypub.frontend.logic.timeline.TimelineSource
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserNotificationsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.refreshedWith
import net.matsudamper.kmp.activitypub.frontend.logic.user.toUserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.toUserNotificationsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.withArrivedNotifications
import net.matsudamper.kmp.activitypub.frontend.logic.user.withArrivedPosts
import net.matsudamper.kmp.activitypub.frontend.logic.user.withDeleted
import net.matsudamper.kmp.activitypub.frontend.logic.user.withPosts
import net.matsudamper.kmp.activitypub.frontend.logic.user.withoutNote

/**
 * タイムラインを購読先ごとに 1 回だけ読み、カラムはその結果を混ぜて出す。
 * カラム同士で購読先が重なっても、同じ投稿を二重に取りに行かない
 */
internal class TimelineFeeds(
    private val scope: CoroutineScope,
    private val userApi: UserApi,
) {
    private val feedsFlow = MutableStateFlow<Map<TimelineSource, Feed>>(mapOf())
    private var readers = mapOf<TimelineSource, FeedReader<*>>()

    /**
     * 読み直しの結果は要求した時点のものなので、その間に購読で削除済みになった行を、読み直しの結果に引き継ぐ。
     * 一度消えた行が戻ることは無いので、読み直しのたびに捨てずに持ち続ける
     */
    private var deletedPostIds = setOf<String>()

    val feeds: StateFlow<Map<TimelineSource, Feed>> = feedsFlow.asStateFlow()

    /**
     * 読む購読先を [sources] だけにする。カラムから外れた購読先は読むのをやめる
     *
     * @return 新しく読み始める購読先。まだ何も読んでいないので、呼び出し側で [refresh] する
     */
    fun retain(sources: Set<TimelineSource>): Set<TimelineSource> {
        val added = sources - readers.keys
        (readers.keys - sources).forEach { removed -> readers[removed]?.close() }
        readers = sources.associateWith { source -> readers[source] ?: createReader(source) }
        feedsFlow.update { feeds -> sources.associateWith { feeds[it] ?: EMPTY_FEED } }
        return added
    }

    /**
     * 読み直す前に呼ぶ。読み直した結果で続きの位置が変わるので、読みかけの続きは捨てる
     */
    fun stopLoadingMore(sources: Set<TimelineSource>) {
        sources.forEach { readers[it]?.stopLoadingMore() }
    }

    /**
     * [sources] を先頭から読み直す
     *
     * @param keepLoadedPages 読み込み済みの続きのページを残すか
     * @return 読めなかった購読先があれば、その理由のどれか 1 つ
     */
    suspend fun refresh(sources: Set<TimelineSource>, keepLoadedPages: Boolean): RefreshFailure? {
        val refreshing = sources.mapNotNull { readers[it] }
        return coroutineScope {
            refreshing.map { reader -> async { reader.refresh(keepLoadedPages) } }.awaitAll().filterNotNull().firstOrNull()
        }
    }

    /**
     * カラムの末尾まで読んだときに、そのカラムの続きを読む。
     * 続きを読むのは、混ぜた並びの末尾を決めている購読先だけ。ほかの購読先はまだ出していない行を持っている
     */
    fun loadMore(sources: Set<TimelineSource>) {
        val currentFeeds = feedsFlow.value
        val watermark = currentFeeds.watermarkOf(sources) ?: return
        sources.forEach { source ->
            val tail = currentFeeds[source]?.takeIf { it.nextCursor != null }?.entries?.lastOrNull() ?: return@forEach
            if (tail.sortKey >= watermark) readers[source]?.loadMore()
        }
    }

    suspend fun onPostArrived(post: UserHomeTimelineResult.Post) {
        val groupId = post.followingGroupId
        readers[if (groupId == null) TimelineSource.MainGroup else TimelineSource.FollowingGroup(groupId)]?.onPostArrived(post)
    }

    suspend fun onNotificationArrived(notification: UserNotificationsResult.Notification) {
        readers[TimelineSource.Notifications]?.onNotificationArrived(notification)
    }

    /**
     * @param postIds 削除済みになった [UserHomeTimelineResult.Post.id]
     */
    suspend fun onPostsDeleted(postIds: List<String>) {
        deletedPostIds = deletedPostIds + postIds
        val deletedIds = postIds.toSet()
        updatePosts { it.withDeleted(deletedIds) }
    }

    /**
     * 読み込み済みの投稿を、取得し直さずに書き換える。いいねや固定など、手元で変えた結果を反映するのに使う
     */
    suspend fun updatePosts(transform: (TimelinePostFields) -> TimelinePostFields) {
        readers.values.forEach { it.updatePosts(transform) }
    }

    /**
     * @param deletableNoteId 自分で消した投稿の [UserHomeTimelineResult.Post.deletableNoteId]
     */
    suspend fun removeNote(deletableNoteId: String) {
        readers.values.forEach { it.removeNote(deletableNoteId) }
    }

    private fun createReader(source: TimelineSource): FeedReader<*> = when (source) {
        TimelineSource.MainGroup -> createPostReader(source, followingGroupId = null)

        is TimelineSource.FollowingGroup -> createPostReader(source, followingGroupId = source.groupId)

        TimelineSource.Notifications -> FeedReader(
            source = source,
            paging = userApi.notificationsPaging(),
            pageOf = { data ->
                when (val result = data.toUserNotificationsResult()) {
                    is UserNotificationsResult.Success -> FeedPage(entries = result.notifications.map { Entry.Notification(it) }, nextCursor = result.nextCursor)
                    UserNotificationsResult.NotLoggedIn -> null
                }
            },
            refreshedWith = { loaded, firstPage, keepLoadedPages, arrivedIds, deletedPostIds ->
                loaded.refreshedWith(firstPage, keepLoadedPages = keepLoadedPages, arrivedNotificationIds = arrivedIds, deletedPostIds = deletedPostIds)
            },
            withArrivedPost = { data, _ -> data },
            withArrivedNotification = { data, notification -> data.withArrivedNotifications(listOf(notification)) },
            withPosts = { data, transform -> data.withPosts(transform) },
            withoutNote = { data, deletableNoteId -> data.withoutNote(deletableNoteId) },
        )
    }

    private fun createPostReader(source: TimelineSource, followingGroupId: String?): FeedReader<*> = FeedReader(
        source = source,
        paging = userApi.timelinePaging(followingGroupId),
        pageOf = { data ->
            when (val result = data.toUserHomeTimelineResult()) {
                is UserHomeTimelineResult.Success -> FeedPage(entries = result.posts.map { Entry.Post(it) }, nextCursor = result.nextCursor)
                UserHomeTimelineResult.NotLoggedIn -> null
            }
        },
        refreshedWith = { loaded, firstPage, keepLoadedPages, arrivedIds, deletedPostIds ->
            loaded.refreshedWith(firstPage, keepLoadedPages = keepLoadedPages, arrivedPostIds = arrivedIds, deletedPostIds = deletedPostIds)
        },
        withArrivedPost = { data, post -> data.withArrivedPosts(listOf(post)) },
        withArrivedNotification = { data, _ -> data },
        withPosts = { data, transform -> data.withPosts(transform) },
        withoutNote = { data, deletableNoteId -> data.withoutNote(deletableNoteId) },
    )

    private fun updateFeed(source: TimelineSource, transform: (Feed) -> Feed) {
        feedsFlow.update { feeds ->
            val feed = feeds[source]
            if (feed == null) feeds else feeds + (source to transform(feed))
        }
    }

    /**
     * 購読先 1 つ分の読み込み。投稿と通知で取得するクエリが違うので、違いは引数の関数で受ける
     *
     * @param pageOf ログインしていなければ null
     */
    private inner class FeedReader<D : Query.Data>(
        private val source: TimelineSource,
        private val paging: CursorPagingModel<D>,
        private val pageOf: (D) -> FeedPage?,
        private val refreshedWith: (loaded: D, firstPage: D, keepLoadedPages: Boolean, arrivedIds: Set<String>, deletedPostIds: Set<String>) -> D,
        private val withArrivedPost: (D, UserHomeTimelineResult.Post) -> D,
        private val withArrivedNotification: (D, UserNotificationsResult.Notification) -> D,
        private val withPosts: (D, (TimelinePostFields) -> TimelinePostFields) -> D,
        private val withoutNote: (D, deletableNoteId: String) -> D,
    ) {
        private val watchJob: Job = scope.launch { paging.watch().collect { apply(it) } }
        private var loadMoreJob: Job? = null

        /**
         * 読み直しの結果は要求した時点のものなので、その間に購読で届いた行を、読み直しの結果に引き継ぐ
         */
        private var arrivedIdsSinceRefresh = setOf<String>()

        suspend fun refresh(keepLoadedPages: Boolean): RefreshFailure? {
            arrivedIdsSinceRefresh = setOf()
            val result = paging.refreshKeepingLoadedPages { loaded, firstPage ->
                refreshedWith(loaded, firstPage, keepLoadedPages, arrivedIdsSinceRefresh, deletedPostIds)
            }
            return when (result) {
                is UpdateOperationResponseResult.Success -> {
                    val data = result.result.data
                    if (data != null && apply(data)) null else RefreshFailure.NotLoggedIn
                }

                is UpdateOperationResponseResult.Error -> RefreshFailure.Failed(result.e?.message)

                is UpdateOperationResponseResult.NoHasMore -> null
            }
        }

        fun loadMore() {
            if (loadMoreJob?.isActive == true) return
            updateFeed(source) { it.copy(append = Append.Loading) }
            loadMoreJob = scope.launch {
                val append = when (val result = paging.fetchNext()) {
                    is UpdateOperationResponseResult.Success -> {
                        val data = result.result.data
                        if (data != null && apply(data)) Append.None else Append.Failed(null)
                    }

                    is UpdateOperationResponseResult.Error -> Append.Failed(result.e?.message)

                    is UpdateOperationResponseResult.NoHasMore -> Append.None
                }
                updateFeed(source) { it.copy(append = append) }
            }
        }

        fun stopLoadingMore() {
            loadMoreJob?.cancel()
            updateFeed(source) { it.copy(append = Append.None) }
        }

        suspend fun onPostArrived(post: UserHomeTimelineResult.Post) {
            arrivedIdsSinceRefresh = arrivedIdsSinceRefresh + post.id
            paging.updateCache { withArrivedPost(it, post) }
        }

        suspend fun onNotificationArrived(notification: UserNotificationsResult.Notification) {
            arrivedIdsSinceRefresh = arrivedIdsSinceRefresh + notification.id
            paging.updateCache { withArrivedNotification(it, notification) }
        }

        suspend fun updatePosts(transform: (TimelinePostFields) -> TimelinePostFields) {
            paging.updateCache { withPosts(it, transform) }
        }

        suspend fun removeNote(deletableNoteId: String) {
            paging.updateCache { withoutNote(it, deletableNoteId) }
        }

        fun close() {
            watchJob.cancel()
            loadMoreJob?.cancel()
        }

        /**
         * 同じ内容を書き込んだときは watch が流れないので、取得結果はここでも反映する
         *
         * @return ログインしていなければ false
         */
        private fun apply(data: D): Boolean {
            val page = pageOf(data)
            updateFeed(source) { feed ->
                feed.copy(
                    // ページの境目で同じ行が両方のページに入ることがある。
                    // 購読で届いた行は先頭に足されるが、遅れて届いた古い投稿のこともあるので、並べる時刻の順に入れ直す。同じ時刻の行は元の順のまま
                    entries = page?.entries.orEmpty().distinctBy { it.key }.sortedByDescending { it.sortKey },
                    nextCursor = page?.nextCursor,
                )
            }
            return page != null
        }
    }

    private class FeedPage(val entries: List<Entry>, val nextCursor: String?)

    /**
     * @param entries 新しい順
     * @param nextCursor 続きが無ければ null
     */
    data class Feed(
        val entries: List<Entry>,
        val nextCursor: String?,
        val append: Append,
    )

    sealed interface Entry {
        /**
         * 同じカラムの中で行を見分ける鍵
         */
        val key: String

        /**
         * 並べる時刻。読めなければ、いちばん古い扱いにする
         */
        val sortKey: Instant

        data class Post(val post: UserHomeTimelineResult.Post) : Entry {
            override val key: String = "post:${post.id}"
            override val sortKey: Instant = parseSortKey(post.sortedAt)
        }

        data class Notification(val notification: UserNotificationsResult.Notification) : Entry {
            override val key: String = "notification:${notification.id}"
            override val sortKey: Instant = parseSortKey(notification.receivedAt)
        }
    }

    sealed interface Append {
        data object None : Append

        data object Loading : Append

        /**
         * 失敗した後に自動で読み直すと、末尾にいる間は失敗を繰り返すので、ボタンを押すまで続きを読まない
         *
         * @param message 理由が分からなければ null
         */
        data class Failed(val message: String?) : Append
    }

    sealed interface RefreshFailure {
        data object NotLoggedIn : RefreshFailure

        /**
         * @param message 理由が分からなければ null
         */
        data class Failed(val message: String?) : RefreshFailure
    }

    private companion object {
        val EMPTY_FEED = Feed(entries = listOf(), nextCursor = null, append = Append.None)

        fun parseSortKey(time: String): Instant = runCatching { Instant.parse(time) }.getOrNull() ?: Instant.DISTANT_PAST
    }
}

/**
 * [sources] を混ぜて新しい順に並べる。購読先ごとの並びはサーバーが返した順のまま保つ。
 * 続きのある購読先のうち、読み込み済みの末尾がいちばん新しいものより古い行は出さない。まだ読んでいない行が間に入るかもしれない
 */
internal fun Map<TimelineSource, TimelineFeeds.Feed>.mergedEntries(sources: Set<TimelineSource>): List<TimelineFeeds.Entry> {
    val watermark = watermarkOf(sources)
    val remaining = sources.map { source -> ArrayDeque(this[source]?.entries.orEmpty()) }.filter { it.isNotEmpty() }.toMutableList()
    return buildList {
        while (remaining.isNotEmpty()) {
            val newest = remaining.maxWith(compareBy<ArrayDeque<TimelineFeeds.Entry>> { it.first().sortKey }.thenBy { it.first().key })
            val entry = newest.removeFirst()
            if (newest.isEmpty()) remaining.remove(newest)
            if (watermark != null && entry.sortKey < watermark) {
                // この購読先のこれより後ろは、もっと古い
                remaining.remove(newest)
            } else {
                add(entry)
            }
        }
    }
}

/**
 * @return 混ぜた並びで出せる、いちばん古い時刻。どの購読先にも続きが無ければ null で、読み込み済みの行をすべて出せる
 */
internal fun Map<TimelineSource, TimelineFeeds.Feed>.watermarkOf(sources: Set<TimelineSource>): Instant? =
    sources.mapNotNull { source -> this[source]?.takeIf { it.nextCursor != null }?.entries?.lastOrNull()?.sortKey }.maxOrNull()

/**
 * @return カラムの末尾に出す読み込みの状態。どれかが読み込み中ならそれを優先する
 */
internal fun Map<TimelineSource, TimelineFeeds.Feed>.appendOf(sources: Set<TimelineSource>): TimelineFeeds.Append {
    val appends = sources.mapNotNull { this[it]?.append }
    return appends.firstOrNull { it is TimelineFeeds.Append.Loading }
        ?: appends.firstOrNull { it is TimelineFeeds.Append.Failed }
        ?: TimelineFeeds.Append.None
}
