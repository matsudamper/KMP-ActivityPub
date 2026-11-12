package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * 積み上げにダイアログの画面しかないときに、下に何も置かずにダイアログを出す。
 * [DialogSceneStrategy] は下に重ねる画面が無いと例外を投げるので、URL でダイアログの画面を直接開くと落ちる
 */
internal class RootDialogSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val entry = entries.singleOrNull() ?: return null
        val dialogProperties = entry.metadata[DialogSceneStrategy.Companion.DialogKey] ?: return null
        return RootDialogScene(
            entry = entry,
            dialogProperties = dialogProperties,
            onBack = onBack,
        )
    }
}

private class RootDialogScene<T : Any>(
    private val entry: NavEntry<T>,
    private val dialogProperties: DialogProperties,
    private val onBack: () -> Unit,
) : Scene<T> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val previousEntries: List<NavEntry<T>> = listOf()
    override val content: @Composable () -> Unit = {
        Dialog(onDismissRequest = onBack, properties = dialogProperties) {
            entry.Content()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RootDialogScene<*>) return false
        return entry == other.entry && dialogProperties == other.dialogProperties
    }

    override fun hashCode(): Int {
        return entry.hashCode() * 31 + dialogProperties.hashCode()
    }
}
