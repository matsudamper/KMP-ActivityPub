package net.matsudamper.kmp.activitypub.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

@Stable
class BackStackNavigator private constructor(
    initialEntries: List<Entry>,
) : Navigator {
    private val entries = mutableStateListOf<Entry>().apply { addAll(initialEntries) }
    private val removedEntryIds = mutableStateListOf<Long>()
    private var nextEntryId: Long = initialEntries.maxOf { it.id } + 1

    val backEnabled: Boolean
        get() = entries.size > 1

    override fun navigate(screen: Screen) {
        entries.add(createEntry(screen))
    }

    override fun replaceAll(screen: Screen) {
        removedEntryIds.addAll(entries.map { it.id })
        entries.clear()
        entries.add(createEntry(screen))
    }

    override fun back() {
        if (backEnabled) {
            removedEntryIds.add(entries.removeAt(entries.lastIndex).id)
        }
    }

    /**
     * 画面ごとの rememberSaveable を積んである間は残し、戻るなどで画面を捨てたときに消す
     */
    @Composable
    fun CurrentScreen(content: @Composable (Screen) -> Unit) {
        val saveableStateHolder = rememberSaveableStateHolder()
        val currentEntry = entries.last()
        saveableStateHolder.SaveableStateProvider(currentEntry.id) {
            content(currentEntry.screen)
        }

        val removedIds = removedEntryIds.toList()
        LaunchedEffect(removedIds) {
            removedIds.forEach { saveableStateHolder.removeState(it) }
            removedEntryIds.removeAll(removedIds)
        }
    }

    private fun createEntry(screen: Screen): Entry {
        return Entry(id = nextEntryId++, screen = screen)
    }

    private data class Entry(
        val id: Long,
        val screen: Screen,
    )

    internal companion object {
        val Saver: Saver<BackStackNavigator, Any> = listSaver<BackStackNavigator, List<String>>(
            save = { navigator ->
                navigator.entries.map { entry -> listOf(entry.id.toString(), entry.screen.toSavedName()) }
            },
            restore = { savedEntries ->
                BackStackNavigator(
                    savedEntries.map { (id, screenName) ->
                        Entry(id = id.toLong(), screen = screenFromSavedName(screenName))
                    },
                )
            },
        )

        fun create(startScreen: Screen): BackStackNavigator {
            return BackStackNavigator(listOf(Entry(id = 0, screen = startScreen)))
        }

        private fun Screen.toSavedName(): String {
            return when (this) {
                Screen.Login -> "login"
                Screen.Home -> "home"
                Screen.Post -> "post"
            }
        }

        private fun screenFromSavedName(name: String): Screen {
            return when (name) {
                "login" -> Screen.Login
                "home" -> Screen.Home
                "post" -> Screen.Post
                else -> error("保存した画面の名前が不明: $name")
            }
        }
    }
}

/**
 * 画面回転などで Activity が作り直されても、積んである画面を復元する
 */
@Composable
fun rememberBackStackNavigator(startScreen: Screen): BackStackNavigator {
    return rememberSaveable(saver = BackStackNavigator.Saver) { BackStackNavigator.create(startScreen) }
}
