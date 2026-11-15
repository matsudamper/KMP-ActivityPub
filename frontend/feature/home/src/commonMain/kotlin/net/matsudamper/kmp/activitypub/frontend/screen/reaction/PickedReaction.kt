package net.matsudamper.kmp.activitypub.frontend.screen.reaction

/**
 * ピッカーで選ばれた絵文字
 */
sealed interface PickedReaction {
    data class Unicode(val emoji: String) : PickedReaction

    /**
     * @param shortcode コロンを含めない名前
     */
    data class Custom(val shortcode: String, val imageUrl: String) : PickedReaction
}
