package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * ダイアログの画面が続けて積まれていても、いちばん上の 1 枚だけを出す。
 * [DialogSceneStrategy] は積まれた数だけダイアログを重ねる。
 * また、下に重ねる画面が無いと例外を投げるので、URL でダイアログの画面を直接開くと落ちる
 */
internal class SingleDialogSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val entry = entries.lastOrNull() ?: return null
        val dialogProperties = entry.dialogProperties() ?: return null
        val entriesBelowDialogs = entries.dropLastWhile { it.dialogProperties() != null }
        return if (entriesBelowDialogs.isEmpty()) {
            RootDialogScene(
                entry = entry,
                previousEntries = entries.dropLast(1),
                dialogProperties = dialogProperties,
                onBack = onBack,
            )
        } else {
            SingleDialogScene(
                entry = entry,
                previousEntries = entries.dropLast(1),
                overlaidEntries = entriesBelowDialogs,
                dialogProperties = dialogProperties,
                onBack = onBack,
            )
        }
    }

    private fun NavEntry<T>.dialogProperties(): DialogProperties? {
        return metadata[DialogSceneStrategy.Companion.DialogKey]
    }
}

private class SingleDialogScene<T : Any>(
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val dialogProperties: DialogProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val content: @Composable () -> Unit = {
        Dialog(onDismissRequest = onBack, properties = dialogProperties) {
            entry.Content()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SingleDialogScene<*>) return false
        return entry == other.entry &&
            previousEntries == other.previousEntries &&
            overlaidEntries == other.overlaidEntries &&
            dialogProperties == other.dialogProperties
    }

    override fun hashCode(): Int {
        return ((entry.hashCode() * 31 + previousEntries.hashCode()) * 31 + overlaidEntries.hashCode()) * 31 + dialogProperties.hashCode()
    }
}

private class RootDialogScene<T : Any>(
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    private val dialogProperties: DialogProperties,
    private val onBack: () -> Unit,
) : Scene<T> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val content: @Composable () -> Unit = {
        Dialog(onDismissRequest = onBack, properties = dialogProperties) {
            entry.Content()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RootDialogScene<*>) return false
        return entry == other.entry && previousEntries == other.previousEntries && dialogProperties == other.dialogProperties
    }

    override fun hashCode(): Int {
        return (entry.hashCode() * 31 + previousEntries.hashCode()) * 31 + dialogProperties.hashCode()
    }
}
