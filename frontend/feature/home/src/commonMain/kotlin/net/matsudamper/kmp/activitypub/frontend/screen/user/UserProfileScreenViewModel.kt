package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.following.FollowMenuController
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserBlockAccountResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserBlockServerResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLikeState
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLinkPreviewsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostBody
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostStamp
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserProfileLikesResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserProfilePostsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserProfileResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserReaction
import net.matsudamper.kmp.activitypub.frontend.logic.user.likeState
import net.matsudamper.kmp.activitypub.frontend.logic.user.needsReloadForPin
import net.matsudamper.kmp.activitypub.frontend.logic.user.withBoosted
import net.matsudamper.kmp.activitypub.frontend.logic.user.withLikeState
import net.matsudamper.kmp.activitypub.frontend.logic.user.withLiked
import net.matsudamper.kmp.activitypub.frontend.logic.user.withPinned
import net.matsudamper.kmp.activitypub.frontend.screen.AuthorTapAction
import net.matsudamper.kmp.activitypub.frontend.screen.CreateOpenOriginalMenuItemsUseCase
import net.matsudamper.kmp.activitypub.frontend.screen.NoteDeletionState
import net.matsudamper.kmp.activitypub.frontend.screen.PostActorLabel
import net.matsudamper.kmp.activitypub.frontend.screen.PostTapAction
import net.matsudamper.kmp.activitypub.frontend.screen.PostedAtRefreshTicker
import net.matsudamper.kmp.activitypub.frontend.screen.QuoteLoad
import net.matsudamper.kmp.activitypub.frontend.screen.ReactionChange
import net.matsudamper.kmp.activitypub.frontend.screen.ReactionConfirmation
import net.matsudamper.kmp.activitypub.frontend.screen.ReactionDecision
import net.matsudamper.kmp.activitypub.frontend.screen.ReactionPickerTarget
import net.matsudamper.kmp.activitypub.frontend.screen.confirm
import net.matsudamper.kmp.activitypub.frontend.screen.createNoteMenuItems
import net.matsudamper.kmp.activitypub.frontend.screen.createPinMenuItems
import net.matsudamper.kmp.activitypub.frontend.screen.createPostItemQuote
import net.matsudamper.kmp.activitypub.frontend.screen.createPostItemUiState
import net.matsudamper.kmp.activitypub.frontend.screen.createProfileMedia
import net.matsudamper.kmp.activitypub.frontend.screen.createTextWithEmojis
import net.matsudamper.kmp.activitypub.frontend.screen.decideLikeTap
import net.matsudamper.kmp.activitypub.frontend.screen.decidePickedReaction
import net.matsudamper.kmp.activitypub.frontend.screen.decideStampTap
import net.matsudamper.kmp.activitypub.frontend.screen.dismiss
import net.matsudamper.kmp.activitypub.frontend.screen.loadQuote
import net.matsudamper.kmp.activitypub.frontend.screen.toDialogUiState
import net.matsudamper.kmp.activitypub.frontend.screen.toLikeState
import net.matsudamper.kmp.activitypub.frontend.screen.withBlurToggled
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileLinkUiState
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class UserProfileScreenViewModel(
    @InjectedParam private val username: String,
    private val api: UserApi,
    private val postedAtRefreshTicker: PostedAtRefreshTicker,
    private val createOpenOriginalMenuItems: CreateOpenOriginalMenuItemsUseCase,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                profile = ProfileState.Loading,
                linkPreviews = mapOf(),
                quotes = mapOf(),
                sensitiveImagesBlurred = true,
                blurToggledImageUrls = setOf(),
                selectedTab = ProfileContentTab.Posts,
                noteDeletion = NoteDeletionState.None,
                reactionPicker = null,
                reactionConfirmation = null,
                block = BlockState.None,
            ),
        )
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var loadMoreLikesJob: Job? = null
    private val followMenuController = FollowMenuController(
        scope = viewModelScope,
        userApi = api,
        showMessage = { message -> events.send { it.showMessage(message) } },
    )

    /**
     * 返事を待たずに表示を切り替えるので、表示には使わない。続けて押されたときに送り違えないよう、返事までは受け付けない
     */
    private var likeChangingPostIds = setOf<String>()
    private var reactionPickerOpenCount = 0
    private var boostChangingPostIds = setOf<String>()

    /**
     * 続けて押されたときに送り違えないよう、返事までは受け付けない
     */
    private var pinChangingNoteIds = setOf<String>()

    /**
     * 表示には使わないので ViewModelState に入れない。入れると失敗時の削除で投稿の listener が作り直され、即座に取り直しを繰り返す
     */
    private var linkPreviewRequestedIds = setOf<String>()

    private val listener = object : UserProfileScreenUiState.Listener {
        override fun onClickRetry() {
            load()
        }

        override fun onClickBack() {
            viewModelScope.launch { events.send { it.navigateBack() } }
        }

        override fun onClickTab(tab: ProfileContentTab) {
            viewModelStateFlow.update { it.copy(selectedTab = tab) }
        }

        override fun onScrolledToEnd() {
            // 失敗した後に自動で読み直すと、末尾にいる間は失敗を繰り返すので、ボタンを押したときだけ読み直す
            val loaded = viewModelStateFlow.value.profile as? ProfileState.Loaded ?: return
            if (viewModelStateFlow.value.selectedTab == ProfileContentTab.Likes) {
                if (loaded.likes.loadErrorMessage != null) return
                loadMoreLikes()
            } else {
                if (loaded.posts.loadErrorMessage != null) return
                loadMore()
            }
        }

        override fun onClickRetryLoadMore() {
            if (viewModelStateFlow.value.selectedTab == ProfileContentTab.Likes) loadMoreLikes() else loadMore()
        }

        override fun onClickSensitiveImagesVisibility() {
            viewModelStateFlow.update {
                it.copy(
                    sensitiveImagesBlurred = !it.sensitiveImagesBlurred,
                    blurToggledImageUrls = setOf(),
                )
            }
        }

        override fun onClickOpenOriginal() {
            val profile = (viewModelStateFlow.value.profile as? ProfileState.Loaded)?.profile ?: return
            // このサーバーのユーザーは actorUri が無いので、画面のプロフィールの URL を開く
            val originalUrl = profile.actorUri ?: "https://${profile.host}/@${profile.username}"
            viewModelScope.launch { events.send { it.openUrl(originalUrl) } }
        }

        override fun onClickBlockServer() {
            val profile = (viewModelStateFlow.value.profile as? ProfileState.Loaded)?.profile ?: return
            if (profile.actorUri == null) return
            viewModelStateFlow.update { it.copy(block = BlockState.Confirming(BlockTarget.Server(profile.host))) }
        }

        override fun onClickBlockAccount() {
            val profile = (viewModelStateFlow.value.profile as? ProfileState.Loaded)?.profile ?: return
            if (profile.actorUri == null) return
            viewModelStateFlow.update { it.copy(block = BlockState.Confirming(BlockTarget.Account(acct = username, displayAcct = "@${profile.username}@${profile.host}"))) }
        }
    }

    private val blockDialogListener = object : UserProfileScreenUiState.BlockDialogListener {
        override fun onClickBlock() {
            block()
        }

        override fun onDismiss() {
            viewModelStateFlow.update { state ->
                if (state.block is BlockState.Confirming) state.copy(block = BlockState.None) else state
            }
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

    val uiStateFlow: StateFlow<UserProfileScreenUiState> =
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
            // ほかの画面で消した投稿もここで外す
            api.deletedNoteIds.collect { noteId ->
                updatePosts { posts -> posts.copy(posts = posts.posts.filterNot { it.deletableNoteId == noteId }) }
                updateLikes { likes -> likes.copy(posts = likes.posts.filterNot { it.deletableNoteId == noteId }) }
            }
        }
        viewModelScope.launch {
            // ほかの画面で変えたブーストもここで反映する
            api.boostChanges.collect { change -> applyBoosted(postId = change.reactionPostId, boosted = change.boosted) }
        }
        viewModelScope.launch {
            // 固定の変更はこの画面のものも他の画面のものもここで届く
            api.pinChanges.collect { change ->
                val loaded = viewModelStateFlow.value.profile as? ProfileState.Loaded
                if (loaded != null && loaded.posts.posts.needsReloadForPin(pinnableNoteId = change.noteId, pinned = change.pinned)) {
                    reloadPostsWithoutLoading()
                    return@collect
                }
                updatePosts { posts -> posts.copy(posts = posts.posts.withPinned(pinnableNoteId = change.noteId, pinned = change.pinned)) }
            }
        }
        viewModelScope.launch {
            // フォローの変更はこの画面のものも他の画面のものもここで届く
            api.followingChanges.collect { change ->
                updateProfile { profile ->
                    if (profile is ProfileState.Loaded && profile.profile.actorUri == change.actorUri) {
                        profile.copy(profile = profile.profile.copy(following = change.following, followingGroupId = change.groupId))
                    } else {
                        profile
                    }
                }
            }
        }
        load()
    }

    /**
     * 表示中の投稿を消さずに最初のページから読み直す。失敗したら今の表示のまま
     */
    private suspend fun reloadPostsWithoutLoading() {
        val result = api.userProfilePosts(acct = username, after = null) as? UserProfilePostsResult.Success ?: return
        loadMoreJob?.cancel()
        updatePosts { PostsState(posts = result.posts, next = result.nextCursor.toNextPosts(), loadErrorMessage = null) }
    }

    /**
     * プロフィールを先に出し、投稿は相手のサーバーを待つことがあるので、出した後で読む
     */
    private fun load() {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        loadMoreLikesJob?.cancel()
        updateProfile { ProfileState.Loading }
        loadJob = viewModelScope.launch {
            val profile =
                when (val result = api.userProfile(username = username)) {
                    is UserProfileResult.Found -> ProfileState.Loaded(
                        profile = result,
                        posts = PostsState(posts = listOf(), next = NextPosts.FirstPage, loadErrorMessage = null),
                        likes = LikesState(posts = listOf(), next = NextPosts.FirstPage, loadErrorMessage = null, available = false),
                    )

                    UserProfileResult.NotFound -> ProfileState.NotFound

                    is UserProfileResult.Failure -> ProfileState.Failed(result.message)
                }
            updateProfile { profile }
            loadMore()
            loadMoreLikes()
        }
    }

    /**
     * 最初のページを読めた相手だけ「いいね」のタブを出す。最初のページを読めなければ、いいねの一覧が無いのと区別できないのでタブを出さない
     */
    private fun loadMoreLikes() {
        if (loadMoreLikesJob?.isActive == true) return
        val loaded = viewModelStateFlow.value.profile as? ProfileState.Loaded ?: return
        val next = loaded.likes.next
        if (next == NextPosts.End) return
        updateLikes { it.copy(loadErrorMessage = null) }
        loadMoreLikesJob = viewModelScope.launch { loadLikes(next) }
    }

    /**
     * 続きが空でもまだ先があれば、続けて読む。開けない投稿だけのページは表示が増えず、画面の末尾の見張りでは読み進められない
     */
    private tailrec suspend fun loadLikes(next: NextPosts) {
        val result = api.userProfileLikes(acct = username, after = (next as? NextPosts.After)?.cursor)
        updateLikes { current ->
            // 読んでいる間に読み直していたら、読み直した一覧に古い続きを足さない
            if (current.next != next) {
                current
            } else {
                when (result) {
                    is UserProfileLikesResult.Success -> current.copy(
                        posts = current.posts + result.posts,
                        next = result.nextCursor.toNextPosts(),
                        available = true,
                    )

                    UserProfileLikesResult.Hidden -> current.copy(next = NextPosts.End, available = false)

                    is UserProfileLikesResult.Failure -> if (current.available) {
                        current.copy(loadErrorMessage = result.message)
                    } else {
                        current.copy(next = NextPosts.End)
                    }
                }
            }
        }
        val nextCursor = (result as? UserProfileLikesResult.Success)?.takeIf { it.posts.isEmpty() }?.nextCursor ?: return
        val continuation = NextPosts.After(nextCursor)
        val applied = (viewModelStateFlow.value.profile as? ProfileState.Loaded)?.likes?.next == continuation
        if (applied) loadLikes(continuation)
    }

    private fun loadMore() {
        if (loadMoreJob?.isActive == true) return
        val loaded = viewModelStateFlow.value.profile as? ProfileState.Loaded ?: return
        val next = loaded.posts.next
        if (next == NextPosts.End) return
        updatePosts { it.copy(loadErrorMessage = null) }
        loadMoreJob = viewModelScope.launch { loadPosts(next) }
    }

    /**
     * 続きが空でもまだ先があれば、続けて読む。固定した投稿だけのページなどは表示が増えず、画面の末尾の見張りでは読み進められない
     */
    private tailrec suspend fun loadPosts(next: NextPosts) {
        val result = api.userProfilePosts(acct = username, after = (next as? NextPosts.After)?.cursor)
        updatePosts { current ->
            // 読んでいる間に読み直していたら、読み直した一覧に古い続きを足さない
            if (current.next != next) {
                current
            } else {
                when (result) {
                    is UserProfilePostsResult.Success -> current.copy(posts = current.posts + result.posts, next = result.nextCursor.toNextPosts())
                    is UserProfilePostsResult.Failure -> current.copy(loadErrorMessage = result.message)
                }
            }
        }
        val nextCursor = (result as? UserProfilePostsResult.Success)?.takeIf { it.posts.isEmpty() }?.nextCursor ?: return
        val continuation = NextPosts.After(nextCursor)
        val applied = (viewModelStateFlow.value.profile as? ProfileState.Loaded)?.posts?.next == continuation
        if (applied) loadPosts(continuation)
    }

    /**
     * @param body 押す前の状態
     */
    private fun toggleLike(reactionPostId: String, body: UserPostBody) {
        if (body.likedByMe == null) return
        if (reactionPostId in likeChangingPostIds) return
        val confirmation = decideLikeTap(reactionPostId, body)
        if (confirmation != null) {
            viewModelStateFlow.update { it.copy(reactionConfirmation = confirmation) }
            return
        }
        val liked = body.plainLikedByMe
        changeLikeState(reactionPostId = reactionPostId, current = body.likeState(), next = body.withLiked(!liked).likeState()) {
            api.setLiked(postId = reactionPostId, liked = !liked)
        }
    }

    /**
     * @param body 開いたときの状態
     */
    private fun openReactionPicker(reactionPostId: String, body: UserPostBody) {
        if (body.likedByMe == null) return
        // 送っている途中の状態を開いたときの状態として持つと、両方失敗したときに送れていない状態へ戻してしまう
        if (reactionPostId in likeChangingPostIds) return
        reactionPickerOpenCount++
        val target = ReactionPickerTarget(reactionPostId = reactionPostId, current = body.likeState(), requestKey = "reaction-picker-$reactionPickerOpenCount")
        viewModelStateFlow.update { it.copy(reactionPicker = target) }
    }

    /**
     * ピッカーで選んだとき
     */
    private fun pickReaction(target: ReactionPickerTarget, reaction: UserReaction) {
        viewModelStateFlow.update { it.copy(reactionPicker = null) }
        handleReactionDecision(decidePickedReaction(target.reactionPostId, target.current, reaction))
    }

    /**
     * ピッカーの「リアクションを外す」を押したとき
     */
    private fun requestReactionRemoval(target: ReactionPickerTarget) {
        val current = target.current as? UserLikeState.Reacted
        viewModelStateFlow.update {
            it.copy(
                reactionPicker = null,
                reactionConfirmation = current?.let { reacted -> ReactionConfirmation(target.reactionPostId, reacted, ReactionChange.Remove) },
            )
        }
    }

    /**
     * @param body 押したときの状態
     */
    private fun clickStamp(reactionPostId: String, body: UserPostBody, stamp: UserPostStamp) {
        if (reactionPostId in likeChangingPostIds) return
        handleReactionDecision(decideStampTap(reactionPostId, body, stamp))
    }

    private fun handleReactionDecision(decision: ReactionDecision) {
        when (decision) {
            is ReactionDecision.Apply -> sendReaction(decision.reactionPostId, decision.current, decision.reaction)
            is ReactionDecision.Confirm -> viewModelStateFlow.update { it.copy(reactionConfirmation = decision.confirmation) }
            ReactionDecision.None -> Unit
        }
    }

    private fun confirmReaction(confirmation: ReactionConfirmation) {
        viewModelStateFlow.update { it.copy(reactionConfirmation = null) }
        val reactionPostId = confirmation.reactionPostId
        when (val next = confirmation.next) {
            ReactionChange.Remove -> sendReaction(reactionPostId, confirmation.current, reaction = null)

            is ReactionChange.React -> sendReaction(reactionPostId, confirmation.current, next.reaction)

            ReactionChange.PlainLike -> changeLikeState(reactionPostId, confirmation.current, UserLikeState.Liked) {
                api.setLiked(postId = reactionPostId, liked = true)
            }
        }
    }

    /**
     * @param reaction null なら外す
     */
    private fun sendReaction(reactionPostId: String, current: UserLikeState, reaction: UserReaction?) {
        changeLikeState(reactionPostId, current, reaction.toLikeState()) {
            api.setReaction(postId = reactionPostId, reaction = reaction)
        }
    }

    /**
     * 返事を待たずに表示を切り替え、送れなかったら戻す
     *
     * @param send 失敗したときに見せる文言を返す
     */
    private fun changeLikeState(reactionPostId: String, current: UserLikeState, next: UserLikeState, send: suspend () -> String?) {
        if (reactionPostId in likeChangingPostIds) return
        likeChangingPostIds = likeChangingPostIds + reactionPostId
        viewModelScope.launch {
            try {
                applyLikeState(reactionPostId = reactionPostId, state = next)
                val error = send()
                if (error != null) {
                    applyLikeState(reactionPostId = reactionPostId, state = current)
                    events.send { it.showMessage(error) }
                }
            } finally {
                likeChangingPostIds = likeChangingPostIds - reactionPostId
            }
        }
    }

    private fun applyLikeState(reactionPostId: String, state: UserLikeState) {
        updatePosts { posts -> posts.copy(posts = posts.posts.withLikeState(reactionPostId = reactionPostId, state = state)) }
        updateLikes { likes ->
            likes.copy(posts = likes.posts.map { if (it.reactionPostId == reactionPostId) it.copy(body = it.body.withLikeState(state)) else it })
        }
    }

    /**
     * @param boostedByMe 押す前の状態
     */
    private fun toggleBoost(postId: String, boostedByMe: Boolean) {
        if (postId in boostChangingPostIds) return
        boostChangingPostIds = boostChangingPostIds + postId
        viewModelScope.launch {
            try {
                applyBoosted(postId = postId, boosted = !boostedByMe)
                val error = api.setBoosted(postId = postId, boosted = !boostedByMe)
                if (error != null) {
                    applyBoosted(postId = postId, boosted = boostedByMe)
                    events.send { it.showMessage(error) }
                }
            } finally {
                boostChangingPostIds = boostChangingPostIds - postId
            }
        }
    }

    private fun setPinned(pinnableNoteId: String, pinned: Boolean) {
        if (pinnableNoteId in pinChangingNoteIds) return
        pinChangingNoteIds = pinChangingNoteIds + pinnableNoteId
        viewModelScope.launch {
            try {
                val error = api.setPinned(noteId = pinnableNoteId, pinned = pinned)
                if (error != null) events.send { it.showMessage(error) }
            } finally {
                pinChangingNoteIds = pinChangingNoteIds - pinnableNoteId
            }
        }
    }

    private fun applyBoosted(postId: String, boosted: Boolean) {
        updatePosts { posts -> posts.copy(posts = posts.posts.withBoosted(reactionPostId = postId, boosted = boosted)) }
        updateLikes { likes ->
            likes.copy(posts = likes.posts.map { if (it.reactionPostId == postId) it.copy(body = it.body.withBoosted(boosted)) else it })
        }
    }

    private fun confirmNoteDeletion(noteId: String) {
        viewModelStateFlow.update { it.copy(noteDeletion = it.noteDeletion.confirm(noteId)) }
    }

    private fun deleteNote() {
        val confirming = viewModelStateFlow.value.noteDeletion as? NoteDeletionState.Confirming ?: return
        val noteId = confirming.noteId
        viewModelStateFlow.update { it.copy(noteDeletion = NoteDeletionState.Deleting(noteId)) }
        viewModelScope.launch {
            try {
                val error = api.deleteNote(noteId)
                events.send { it.showMessage(error ?: "投稿を削除しました") }
            } finally {
                viewModelStateFlow.update { it.copy(noteDeletion = NoteDeletionState.None) }
            }
        }
    }

    private fun updateProfile(transform: (ProfileState) -> ProfileState) {
        viewModelStateFlow.update { it.copy(profile = transform(it.profile)) }
    }

    private fun updatePosts(transform: (PostsState) -> PostsState) {
        updateProfile { profile -> if (profile is ProfileState.Loaded) profile.copy(posts = transform(profile.posts)) else profile }
    }

    private fun updateLikes(transform: (LikesState) -> LikesState) {
        updateProfile { profile -> if (profile is ProfileState.Loaded) profile.copy(likes = transform(profile.likes)) else profile }
    }

    private fun toggleImageBlur(imageUrls: List<String>, toggled: Boolean) {
        viewModelStateFlow.update {
            it.copy(blurToggledImageUrls = it.blurToggledImageUrls.withBlurToggled(imageUrls, toggled))
        }
    }

    private fun loadLinkPreviews(postId: String) {
        if (postId in linkPreviewRequestedIds) return
        linkPreviewRequestedIds = linkPreviewRequestedIds + postId
        viewModelScope.launch {
            when (val result = api.linkPreviews(postId)) {
                is UserLinkPreviewsResult.Success -> {
                    viewModelStateFlow.update { it.copy(linkPreviews = it.linkPreviews + (postId to result.previews)) }
                }

                // カードは URL だけでも出ているので、取れなくても知らせない。次に画面に入ったときに取り直す
                is UserLinkPreviewsResult.Failure,
                UserLinkPreviewsResult.NotLoggedIn,
                -> linkPreviewRequestedIds = linkPreviewRequestedIds - postId
            }
        }
    }

    private fun createUiState(viewModelState: ViewModelState, followMenu: FollowMenuController.Snapshot): UserProfileScreenUiState {
        return UserProfileScreenUiState(
            content = when (val profile = viewModelState.profile) {
                ProfileState.Loading -> UserProfileScreenUiState.Content.Loading
                is ProfileState.Loaded -> createLoadedContent(profile, viewModelState, followMenu)
                ProfileState.NotFound -> UserProfileScreenUiState.Content.NotFound
                is ProfileState.Failed -> UserProfileScreenUiState.Content.Error(profile.message)
            },
            deleteNoteDialog = viewModelState.noteDeletion.toDialogUiState(deleteNoteDialogListener),
            reactionPickerDialog = viewModelState.reactionPicker?.let { target ->
                target.toDialogUiState(
                    onPick = { reaction -> pickReaction(target, reaction) },
                    onRemove = { requestReactionRemoval(target) },
                    onDismiss = { viewModelStateFlow.update { it.copy(reactionPicker = null) } },
                )
            },
            reactionConfirmDialog = viewModelState.reactionConfirmation?.let { confirmation ->
                confirmation.toDialogUiState(
                    onConfirm = { confirmReaction(confirmation) },
                    onDismiss = { viewModelStateFlow.update { it.copy(reactionConfirmation = null) } },
                )
            },
            addFollowingGroupDialog = followMenuController.addGroupDialog(followMenu),
            blockDialog = when (val block = viewModelState.block) {
                BlockState.None -> null
                is BlockState.Confirming -> createBlockDialog(target = block.target, buttonsEnabled = true)
                is BlockState.Blocking -> createBlockDialog(target = block.target, buttonsEnabled = false)
            },
            openOriginalMenuItemVisible = viewModelState.profile is ProfileState.Loaded,
            sensitiveImagesBlurred = viewModelState.sensitiveImagesBlurred,
            blockMenuItemsVisible = (viewModelState.profile as? ProfileState.Loaded)?.profile?.actorUri != null,
            listener = listener,
        )
    }

    private fun createBlockDialog(target: BlockTarget, buttonsEnabled: Boolean): UserProfileScreenUiState.BlockDialog = when (target) {
        is BlockTarget.Server -> UserProfileScreenUiState.BlockDialog(
            title = "サーバーをブロック",
            message = "${target.host} のユーザーの投稿・ブースト・通知を表示しなくなります。設定のサーバーブロックから解除できます",
            buttonsEnabled = buttonsEnabled,
            listener = blockDialogListener,
        )

        is BlockTarget.Account -> UserProfileScreenUiState.BlockDialog(
            title = "アカウントをブロック",
            message = "${target.displayAcct} の投稿・ブースト・通知を表示しなくなります。設定のアカウントブロックから解除できます",
            buttonsEnabled = buttonsEnabled,
            listener = blockDialogListener,
        )
    }

    private fun block() {
        val confirming = viewModelStateFlow.value.block as? BlockState.Confirming ?: return
        viewModelStateFlow.update { it.copy(block = BlockState.Blocking(confirming.target)) }
        viewModelScope.launch {
            val message =
                try {
                    when (val target = confirming.target) {
                        is BlockTarget.Server -> when (val result = api.blockServer(target.host)) {
                            is UserBlockServerResult.Blocked -> "${result.host} をブロックしました"
                            is UserBlockServerResult.Failure -> result.message
                        }

                        is BlockTarget.Account -> when (val result = api.blockAccount(target.acct)) {
                            is UserBlockAccountResult.Blocked -> "@${result.account.acct} をブロックしました"
                            is UserBlockAccountResult.Failure -> result.message
                        }
                    }
                } finally {
                    viewModelStateFlow.update { it.copy(block = BlockState.None) }
                }
            events.send { it.showMessage(message) }
        }
    }

    private fun loadQuote(postId: String) {
        if (postId in viewModelStateFlow.value.quotes) return
        viewModelStateFlow.update { it.copy(quotes = it.quotes + (postId to QuoteLoad.Loading)) }
        viewModelScope.launch {
            val load = api.loadQuote(postId)
            viewModelStateFlow.update { state ->
                state.copy(quotes = if (load == null) state.quotes - postId else state.quotes + (postId to load))
            }
        }
    }

    private fun navigateToPostDetail(postId: String) {
        viewModelScope.launch { events.send { it.navigateToPostDetail(postId) } }
    }

    private fun createLoadedContent(
        loaded: ProfileState.Loaded,
        viewModelState: ViewModelState,
        followMenu: FollowMenuController.Snapshot,
    ): UserProfileScreenUiState.Content.Loaded {
        val profile = loaded.profile
        val posts = loaded.posts
        val acct = "@${profile.username}@${profile.host}"
        val media = createProfileMedia(
            posts = posts.posts.filter { it.boostedNoteAuthor == null },
            sensitiveImagesBlurred = viewModelState.sensitiveImagesBlurred,
            onOpenPost = ::navigateToPostDetail,
        )
        return UserProfileScreenUiState.Content.Loaded(
            acct = acct,
            iconUrl = profile.iconUrl,
            headerUrl = profile.headerUrl,
            displayName = createTextWithEmojis(text = profile.displayName, emojis = profile.displayNameEmojis),
            biography = profile.biography,
            links = profile.links.map { link ->
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
            followMenu = profile.actorUri?.let { actorUri ->
                followMenuController.menu(
                    snapshot = followMenu,
                    actorUri = actorUri,
                    receivedFollowing = profile.following,
                    receivedGroupId = profile.followingGroupId,
                )
            },
            tabs = if (loaded.likes.available) {
                listOf(ProfileContentTab.Posts, ProfileContentTab.Media, ProfileContentTab.Likes)
            } else {
                listOf(ProfileContentTab.Posts, ProfileContentTab.Media)
            },
            selectedTab = viewModelState.selectedTab,
            posts = posts.posts.map { post ->
                val previews = viewModelState.linkPreviews[post.postId].orEmpty()
                val boostedNoteAuthor = post.boostedNoteAuthor
                createPostItemUiState(
                    pinned = post.pinned,
                    deleted = false,
                    boostUndone = false,
                    actorLabel = if (boostedNoteAuthor == null) {
                        null
                    } else {
                        PostActorLabel.Boost(
                            actor = UserHomeTimelineResult.Actor(
                                username = profile.username,
                                host = profile.host,
                                displayName = profile.displayName,
                                displayNameEmojis = profile.displayNameEmojis,
                                iconUrl = profile.iconUrl,
                            ),
                            byViewer = profile.viewerIsOwner,
                            navigateToProfile = null,
                        )
                    },
                    iconUrl = if (boostedNoteAuthor == null) profile.iconUrl else boostedNoteAuthor.iconUrl,
                    displayName = boostedNoteAuthor?.displayName ?: profile.displayName,
                    displayNameEmojis = boostedNoteAuthor?.displayNameEmojis ?: profile.displayNameEmojis,
                    acct = if (boostedNoteAuthor == null) acct else "@${boostedNoteAuthor.username}@${boostedNoteAuthor.host}",
                    body = post.body,
                    linkPreviews = previews,
                    quote = createPostItemQuote(
                        hasQuoteTarget = post.hasQuoteTarget,
                        load = viewModelState.quotes[post.postId],
                        onOpenPostDetail = ::navigateToPostDetail,
                        onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
                        onOpenProfile = { acct -> viewModelScope.launch { events.send { it.navigateToUserProfile(acct) } } },
                    ),
                    sensitiveImagesBlurred = viewModelState.sensitiveImagesBlurred,
                    blurToggledImageUrls = viewModelState.blurToggledImageUrls,
                    authorTapAction = if (boostedNoteAuthor == null) {
                        AuthorTapAction.AlreadyOnAuthorProfile
                    } else {
                        AuthorTapAction.OpenProfile {
                            viewModelScope.launch {
                                events.send { it.navigateToUserProfile("${boostedNoteAuthor.username}@${boostedNoteAuthor.host}") }
                            }
                        }
                    },
                    menuItems =
                    createOpenOriginalMenuItems(post.body.shareUrl) { url -> viewModelScope.launch { events.send { it.openUrl(url) } } } +
                        createPinMenuItems(post.pinnableNoteId, post.pinned, ::setPinned) +
                        createNoteMenuItems(post.deletableNoteId, ::confirmNoteDeletion),
                    postTapAction = PostTapAction.OpenPostDetail(postId = post.postId, navigate = ::navigateToPostDetail),
                    onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
                    onOpenProfile = { acct -> viewModelScope.launch { events.send { it.navigateToUserProfile(acct) } } },
                    onOpenImage = { urls, index -> viewModelScope.launch { events.send { it.navigateToImage(urls, index) } } },
                    onToggleImageBlur = { imageUrls, toggled -> toggleImageBlur(imageUrls, toggled) },
                    onShare = { url -> viewModelScope.launch { events.send { it.shareUrl(url) } } },
                    onToggleLike = {
                        val reactionPostId = post.reactionPostId
                        if (reactionPostId != null) toggleLike(reactionPostId = reactionPostId, body = post.body)
                    },
                    onToggleBoost = {
                        val reactionPostId = post.reactionPostId
                        val boostedByMe = post.body.boostedByMe
                        if (reactionPostId != null && boostedByMe != null) toggleBoost(postId = reactionPostId, boostedByMe = boostedByMe)
                    },
                    onQuote = { viewModelScope.launch { events.send { it.navigateToQuotePost(post.postId) } } },
                    onOpenReactionPicker = {
                        val reactionPostId = post.reactionPostId
                        if (reactionPostId != null) openReactionPicker(reactionPostId = reactionPostId, body = post.body)
                    },
                    onClickStamp = { stamp ->
                        val reactionPostId = post.reactionPostId
                        if (reactionPostId != null) clickStamp(reactionPostId = reactionPostId, body = post.body, stamp = stamp)
                    },
                    onOpenRevisions = null,
                    onVisible = {
                        if (post.body.linkUrls.isNotEmpty()) {
                            loadLinkPreviews(post.postId)
                        }
                        if (post.hasQuoteTarget) {
                            loadQuote(post.postId)
                        }
                    },
                )
            },
            postsEmptyMessage = if (posts.posts.isEmpty() && posts.next == NextPosts.End) "表示できる投稿はありません" else null,
            postsFooter = createPostsFooter(posts),
            media = media,
            mediaEmptyMessage = if (media.isEmpty() && posts.next == NextPosts.End) "表示できるメディアはありません" else null,
            likes = loaded.likes.posts.map { post -> createLikedPostItem(post, viewModelState) },
            likesEmptyMessage = if (loaded.likes.posts.isEmpty() && loaded.likes.next == NextPosts.End) "表示できるいいねはありません" else null,
            likesFooter = createLikesFooter(loaded.likes),
        )
    }

    /**
     * いいねした投稿は、プロフィールの本人ではない人の投稿なので、投稿者のプロフィールを開けるようにする
     */
    private fun createLikedPostItem(post: UserHomeTimelineResult.Post, viewModelState: ViewModelState): PostItemUiState {
        val host = post.host
        val profileAcct = if (host == null) post.username else "${post.username}@$host"
        return createPostItemUiState(
            pinned = false,
            deleted = post.deleted,
            boostUndone = false,
            actorLabel = null,
            iconUrl = post.iconUrl,
            displayName = post.displayName,
            displayNameEmojis = post.displayNameEmojis,
            acct = "@$profileAcct",
            body = post.body,
            linkPreviews = viewModelState.linkPreviews[post.id].orEmpty(),
            quote = createPostItemQuote(
                hasQuoteTarget = post.hasQuoteTarget,
                load = viewModelState.quotes[post.id],
                onOpenPostDetail = ::navigateToPostDetail,
                onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
                onOpenProfile = { acct -> viewModelScope.launch { events.send { it.navigateToUserProfile(acct) } } },
            ),
            sensitiveImagesBlurred = viewModelState.sensitiveImagesBlurred,
            blurToggledImageUrls = viewModelState.blurToggledImageUrls,
            authorTapAction = AuthorTapAction.OpenProfile {
                viewModelScope.launch { events.send { it.navigateToUserProfile(profileAcct) } }
            },
            menuItems = createOpenOriginalMenuItems(post.body.shareUrl) { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
            postTapAction = PostTapAction.OpenPostDetail(postId = post.id, navigate = ::navigateToPostDetail),
            onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
            onOpenProfile = { acct -> viewModelScope.launch { events.send { it.navigateToUserProfile(acct) } } },
            onOpenImage = { urls, index -> viewModelScope.launch { events.send { it.navigateToImage(urls, index) } } },
            onToggleImageBlur = { imageUrls, toggled -> toggleImageBlur(imageUrls, toggled) },
            onShare = { url -> viewModelScope.launch { events.send { it.shareUrl(url) } } },
            onToggleLike = { toggleLike(reactionPostId = post.reactionPostId, body = post.body) },
            onToggleBoost = {
                val boostedByMe = post.body.boostedByMe
                if (boostedByMe != null) toggleBoost(postId = post.reactionPostId, boostedByMe = boostedByMe)
            },
            onQuote = { viewModelScope.launch { events.send { it.navigateToQuotePost(post.id) } } },
            onOpenReactionPicker = { openReactionPicker(reactionPostId = post.reactionPostId, body = post.body) },
            onClickStamp = { stamp -> clickStamp(reactionPostId = post.reactionPostId, body = post.body, stamp = stamp) },
            onOpenRevisions = null,
            onVisible = {
                if (post.body.linkUrls.isNotEmpty()) {
                    loadLinkPreviews(post.id)
                }
                if (post.hasQuoteTarget) {
                    loadQuote(post.id)
                }
            },
        )
    }

    private fun createLikesFooter(likes: LikesState): UserProfileScreenUiState.PostsFooter {
        val loadErrorMessage = likes.loadErrorMessage
        return when {
            loadErrorMessage != null -> UserProfileScreenUiState.PostsFooter.Error(loadErrorMessage)
            likes.next != NextPosts.End -> UserProfileScreenUiState.PostsFooter.Loading
            else -> UserProfileScreenUiState.PostsFooter.None
        }
    }

    private fun createPostsFooter(posts: PostsState): UserProfileScreenUiState.PostsFooter {
        val loadErrorMessage = posts.loadErrorMessage
        return when {
            loadErrorMessage != null -> UserProfileScreenUiState.PostsFooter.Error(loadErrorMessage)
            posts.next != NextPosts.End -> UserProfileScreenUiState.PostsFooter.Loading
            else -> UserProfileScreenUiState.PostsFooter.None
        }
    }

    private fun String?.toNextPosts(): NextPosts = if (this == null) NextPosts.End else NextPosts.After(this)

    internal interface Event {
        fun navigateBack()

        fun showMessage(message: String)

        fun openUrl(url: String)

        fun shareUrl(url: String)

        fun navigateToImage(urls: List<String>, index: Int)

        fun navigateToPostDetail(postId: String)

        fun navigateToQuotePost(postId: String)

        fun navigateToUserProfile(username: String)
    }

    private data class ViewModelState(
        val profile: ProfileState,
        val linkPreviews: Map<String, List<UserLinkPreviewsResult.LinkPreview>>,
        /**
         * 投稿の id ごとの引用先
         */
        val quotes: Map<String, QuoteLoad>,
        val sensitiveImagesBlurred: Boolean,
        val blurToggledImageUrls: Set<String>,
        val selectedTab: ProfileContentTab,
        val noteDeletion: NoteDeletionState,
        val reactionPicker: ReactionPickerTarget?,
        val reactionConfirmation: ReactionConfirmation?,
        val block: BlockState,
    )

    private sealed interface BlockState {
        data object None : BlockState

        data class Confirming(val target: BlockTarget) : BlockState

        data class Blocking(val target: BlockTarget) : BlockState
    }

    private sealed interface BlockTarget {
        data class Server(val host: String) : BlockTarget

        /**
         * @param acct プロフィールを開いたときの acct。WebFinger のドメインとアクターのホストが違うことがあるので、ホストから組み立て直さない
         * @param displayAcct ダイアログに出す `@username@host`
         */
        data class Account(val acct: String, val displayAcct: String) : BlockTarget
    }

    private sealed interface ProfileState {
        data object Loading : ProfileState

        data class Loaded(
            val profile: UserProfileResult.Found,
            val posts: PostsState,
            val likes: LikesState,
        ) : ProfileState

        data object NotFound : ProfileState

        data class Failed(
            val message: String,
        ) : ProfileState
    }

    /**
     * @param next 次に読むページ
     * @param loadErrorMessage 投稿を読めなかったときの理由。読めていれば null
     */
    private data class PostsState(
        val posts: List<UserProfileResult.Post>,
        val next: NextPosts,
        val loadErrorMessage: String?,
    )

    /**
     * @param next 次に読むページ
     * @param loadErrorMessage 続きを読めなかったときの理由。読めていれば null
     * @param available いいねの一覧を読めたか。読めた相手だけ「いいね」のタブを出す
     */
    private data class LikesState(
        val posts: List<UserHomeTimelineResult.Post>,
        val next: NextPosts,
        val loadErrorMessage: String?,
        val available: Boolean,
    )

    private sealed interface NextPosts {
        data object FirstPage : NextPosts

        data class After(val cursor: String) : NextPosts

        data object End : NextPosts
    }
}
