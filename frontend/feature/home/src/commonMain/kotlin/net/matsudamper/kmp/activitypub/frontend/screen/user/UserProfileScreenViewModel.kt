package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserProfileResult
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class UserProfileScreenViewModel(
    @InjectedParam private val username: String,
    private val api: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState.Loading)
    private var loadJob: Job? = null
    private var followingChangeJob: Job? = null

    private val listener = object : UserProfileScreenUiState.Listener {
        override fun onClickRetry() {
            load()
        }

        override fun onClickBack() {
            viewModelScope.launch { events.send { it.navigateBack() } }
        }

        override fun onClickFollow() {
            changeFollowing()
        }
    }

    private val postItemListener = object : PostItemUiState.Listener {
        override fun onVisible() = Unit

        override fun onClickAuthor() = Unit
    }

    private val mutableUiStateFlow: MutableStateFlow<UserProfileScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value))
    val uiStateFlow: StateFlow<UserProfileScreenUiState> = mutableUiStateFlow.asStateFlow()

    init {
        viewModelScope.launch {
            viewModelStateFlow.collect { viewModelState ->
                mutableUiStateFlow.value = createUiState(viewModelState)
            }
        }
        viewModelScope.launch {
            // フォローの変更はこの画面のものも他の画面のものもここで届く
            api.followingChanges.collect { change ->
                viewModelStateFlow.update { state ->
                    if (state is ViewModelState.Loaded && state.profile.actorUri == change.actorUri) {
                        state.copy(profile = state.profile.copy(following = change.following))
                    } else {
                        state
                    }
                }
            }
        }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        viewModelStateFlow.value = ViewModelState.Loading
        loadJob = viewModelScope.launch {
            viewModelStateFlow.value =
                when (val result = api.userProfile(username = username)) {
                    is UserProfileResult.Found -> ViewModelState.Loaded(profile = result, followingChangeInProgress = false)
                    UserProfileResult.NotFound -> ViewModelState.NotFound
                    is UserProfileResult.Failure -> ViewModelState.Failed(result.message)
                }
        }
    }

    private fun changeFollowing() {
        if (followingChangeJob?.isActive == true) return
        val loaded = viewModelStateFlow.value as? ViewModelState.Loaded ?: return
        val actorUri = loaded.profile.actorUri ?: return
        followingChangeJob = viewModelScope.launch {
            updateFollowingChangeInProgress(true)
            try {
                val error = api.setFollowing(actorUri = actorUri, followed = !loaded.profile.following)
                if (error != null) {
                    events.send { it.showMessage(error) }
                }
            } finally {
                updateFollowingChangeInProgress(false)
            }
        }
    }

    private fun updateFollowingChangeInProgress(inProgress: Boolean) {
        viewModelStateFlow.update { state ->
            if (state is ViewModelState.Loaded) state.copy(followingChangeInProgress = inProgress) else state
        }
    }

    private fun createUiState(viewModelState: ViewModelState): UserProfileScreenUiState {
        return UserProfileScreenUiState(
            content = when (viewModelState) {
                ViewModelState.Loading -> UserProfileScreenUiState.Content.Loading
                is ViewModelState.Loaded -> createLoadedContent(viewModelState)
                ViewModelState.NotFound -> UserProfileScreenUiState.Content.NotFound
                is ViewModelState.Failed -> UserProfileScreenUiState.Content.Error(viewModelState.message)
            },
            listener = listener,
        )
    }

    private fun createLoadedContent(loaded: ViewModelState.Loaded): UserProfileScreenUiState.Content.Loaded {
        val profile = loaded.profile
        val acct = "@${profile.username}@${profile.host}"
        return UserProfileScreenUiState.Content.Loaded(
            acct = acct,
            iconUrl = profile.iconUrl,
            displayName = profile.displayName,
            biography = profile.biography,
            followButton = if (profile.actorUri == null) {
                null
            } else {
                UserProfileScreenUiState.FollowButton(
                    text = if (profile.following) "フォロー中・解除" else "フォロー",
                    enabled = !loaded.followingChangeInProgress,
                )
            },
            posts = profile.posts.map { post ->
                PostItemUiState(
                    iconUrl = profile.iconUrl,
                    displayName = profile.displayName,
                    acct = acct,
                    body = post.content,
                    postedAt = post.publishedAt,
                    linkPreviews = listOf(),
                    // 開いているのがその人の画面なので、押しても移る先が無い
                    authorClickable = false,
                    listener = postItemListener,
                )
            },
            postsEmptyMessage = if (profile.posts.isEmpty()) "表示できる投稿はありません" else null,
        )
    }

    internal interface Event {
        fun navigateBack()

        fun showMessage(message: String)
    }

    private sealed interface ViewModelState {
        data object Loading : ViewModelState

        data class Loaded(
            val profile: UserProfileResult.Found,
            val followingChangeInProgress: Boolean,
        ) : ViewModelState

        data object NotFound : ViewModelState

        data class Failed(
            val message: String,
        ) : ViewModelState
    }
}
