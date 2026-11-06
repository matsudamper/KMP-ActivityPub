package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot

/**
 * ripple は interactions を見て色を付けるので、hover だけを流さないことで、押したときの ripple を残して hover の色だけを消す
 */
internal class HoverIgnoringInteractionSource : MutableInteractionSource {
    private val source = MutableInteractionSource()

    override val interactions: Flow<Interaction> = source.interactions.filterNot { it is HoverInteraction }

    override suspend fun emit(interaction: Interaction) {
        source.emit(interaction)
    }

    override fun tryEmit(interaction: Interaction): Boolean = source.tryEmit(interaction)
}
