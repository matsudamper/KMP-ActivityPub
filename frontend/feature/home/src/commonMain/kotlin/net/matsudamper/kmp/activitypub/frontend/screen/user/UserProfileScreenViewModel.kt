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
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLinkPreviewsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserProfileResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.withLiked
import net.matsudamper.kmp.activitypub.frontend.screen.AuthorTapAction
import net.matsudamper.kmp.activitypub.frontend.screen.Boost
import net.matsudamper.kmp.activitypub.frontend.screen.NoteDeletionState
import net.matsudamper.kmp.activitypub.frontend.screen.PostTapAction
import net.matsudamper.kmp.activitypub.frontend.screen.PostedAtRefreshTicker
import net.matsudamper.kmp.activitypub.frontend.screen.confirm
import net.matsudamper.kmp.activitypub.frontend.screen.createNoteMenuItems
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
internal class UserProfileScreenViewModel(
    @InjectedParam private val username: String,
    private val api: UserApi,
    private val postedAtRefreshTicker: PostedAtRefreshTicker,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                profile = ProfileState.Loading,
                linkPreviews = mapOf(),
                blurToggledImageUrls = setOf(),
                selectedTab = ProfileContentTab.Posts,
                noteDeletion = NoteDeletionState.None,
            ),
        )
    private var loadJob: Job? = null
    private val followMenuController = FollowMenuController(
        scope = viewModelScope,
        userApi = api,
        showMessage = { message -> events.send { it.showMessage(message) } },
    )

    /**
     * 返事を待たずに表示を切り替えるので、表示には使わない。続けて押されたときに送り違えないよう、返事までは受け付けない
     */
    private var likeChangingPostIds = setOf<String>()

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
                updateProfile { profile ->
                    if (profile is ProfileState.Loaded) {
                        profile.copy(profile = profile.profile.copy(posts = profile.profile.posts.filterNot { it.deletableNoteId == noteId }))
                    } else {
                        profile
                    }
                }
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

    private fun load() {
        loadJob?.cancel()
        updateProfile { ProfileState.Loading }
        loadJob = viewModelScope.launch {
            val profile =
                when (val result = api.userProfile(username = username)) {
                    is UserProfileResult.Found -> ProfileState.Loaded(profile = result)
                    UserProfileResult.NotFound -> ProfileState.NotFound
                    is UserProfileResult.Failure -> ProfileState.Failed(result.message)
                }
            updateProfile { profile }
        }
    }

    /**
     * @param likedByMe 押す前の状態
     */
    private fun toggleLike(likePostId: String, likedByMe: Boolean) {
        if (likePostId in likeChangingPostIds) return
        likeChangingPostIds = likeChangingPostIds + likePostId
        viewModelScope.launch {
            try {
                applyLiked(likePostId = likePostId, liked = !likedByMe)
                val error = api.setLiked(postId = likePostId, liked = !likedByMe)
                if (error != null) {
                    applyLiked(likePostId = likePostId, liked = likedByMe)
                    events.send { it.showMessage(error) }
                }
            } finally {
                likeChangingPostIds = likeChangingPostIds - likePostId
            }
        }
    }

    private fun applyLiked(likePostId: String, liked: Boolean) {
        updateProfile { profile ->
            if (profile is ProfileState.Loaded) profile.copy(profile = profile.profile.withLiked(likePostId = likePostId, liked = liked)) else profile
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

    private fun toggleImageBlur(imageUrls: List<String>, toggled: Boolean) {
        viewModelStateFlow.update {
            it.copy(blurToggledImageUrls = it.blurToggledImageUrls.withBlurToggled(imageUrls, toggled))
        }
    }

    private fun loadLinkPreviews(linkPreviewId: String) {
        if (linkPreviewId in linkPreviewRequestedIds) return
        linkPreviewRequestedIds = linkPreviewRequestedIds + linkPreviewId
        viewModelScope.launch {
            when (val result = api.linkPreviews(linkPreviewId)) {
                is UserLinkPreviewsResult.Success -> {
                    viewModelStateFlow.update { it.copy(linkPreviews = it.linkPreviews + (linkPreviewId to result.previews)) }
                }

                // カードは URL だけでも出ているので、取れなくても知らせない。次に画面に入ったときに取り直す
                is UserLinkPreviewsResult.Failure,
                UserLinkPreviewsResult.NotLoggedIn,
                -> linkPreviewRequestedIds = linkPreviewRequestedIds - linkPreviewId
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
            addFollowingGroupDialog = followMenuController.addGroupDialog(followMenu),
            listener = listener,
        )
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
        val acct = "@${profile.username}@${profile.host}"
        val media = createProfileMedia(profile.posts.filter { it.boostedNoteAuthor == null }, ::navigateToPostDetail)
        return UserProfileScreenUiState.Content.Loaded(
            acct = acct,
            iconUrl = profile.iconUrl,
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
            selectedTab = viewModelState.selectedTab,
            posts = profile.posts.map { post ->
                val linkPreviewId = post.linkPreviewId
                val previews = linkPreviewId?.let { viewModelState.linkPreviews[it] }.orEmpty()
                val boostedNoteAuthor = post.boostedNoteAuthor
                createPostItemUiState(
                    boostedBy = if (boostedNoteAuthor == null) {
                        null
                    } else {
                        Boost(
                            booster = UserHomeTimelineResult.Booster(
                                username = profile.username,
                                host = profile.host,
                                displayName = profile.displayName,
                                displayNameEmojis = profile.displayNameEmojis,
                                iconUrl = profile.iconUrl,
                            ),
                            navigateToProfile = null,
                        )
                    },
                    iconUrl = if (boostedNoteAuthor == null) profile.iconUrl else boostedNoteAuthor.iconUrl,
                    displayName = boostedNoteAuthor?.displayName ?: profile.displayName,
                    displayNameEmojis = boostedNoteAuthor?.displayNameEmojis ?: profile.displayNameEmojis,
                    acct = if (boostedNoteAuthor == null) acct else "@${boostedNoteAuthor.username}@${boostedNoteAuthor.host}",
                    body = post.body,
                    linkPreviews = previews,
                    sensitiveImagesBlurred = true,
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
                    menuItems = createNoteMenuItems(post.deletableNoteId, ::confirmNoteDeletion),
                    postTapAction = PostTapAction.OpenPostDetail(postId = post.postId, navigate = ::navigateToPostDetail),
                    onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
                    onOpenImage = { urls, index -> viewModelScope.launch { events.send { it.navigateToImage(urls, index) } } },
                    onToggleImageBlur = { imageUrls, toggled -> toggleImageBlur(imageUrls, toggled) },
                    onShare = { url -> viewModelScope.launch { events.send { it.shareUrl(url) } } },
                    onToggleLike = {
                        val likePostId = post.likePostId
                        val likedByMe = post.body.likedByMe
                        if (likePostId != null && likedByMe != null) toggleLike(likePostId = likePostId, likedByMe = likedByMe)
                    },
                    onVisible = {
                        if (linkPreviewId != null && post.body.linkUrls.isNotEmpty()) {
                            loadLinkPreviews(linkPreviewId)
                        }
                    },
                )
            },
            postsEmptyMessage = if (profile.posts.isEmpty()) "表示できる投稿はありません" else null,
            media = media,
            mediaEmptyMessage = if (media.isEmpty()) "表示できるメディアはありません" else null,
        )
    }

    internal interface Event {
        fun navigateBack()

        fun showMessage(message: String)

        fun openUrl(url: String)

        fun shareUrl(url: String)

        fun navigateToImage(urls: List<String>, index: Int)

        fun navigateToPostDetail(postId: String)

        fun navigateToUserProfile(username: String)
    }

    private data class ViewModelState(
        val profile: ProfileState,
        val linkPreviews: Map<String, List<UserLinkPreviewsResult.LinkPreview>>,
        val blurToggledImageUrls: Set<String>,
        val selectedTab: ProfileContentTab,
        val noteDeletion: NoteDeletionState,
    )

    private sealed interface ProfileState {
        data object Loading : ProfileState

        data class Loaded(
            val profile: UserProfileResult.Found,
        ) : ProfileState

        data object NotFound : ProfileState

        data class Failed(
            val message: String,
        ) : ProfileState
    }
}
