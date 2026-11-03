package net.matsudamper.kmp.activitypub.frontend.ui

/**
 * [toPostImageOrNull] で縮め直すときの長い辺の候補。大きい順
 */
internal fun postImageSideCandidates(
    sourceLongestSide: Int,
    maxSidePx: Int,
): List<Int> =
    generateSequence(minOf(sourceLongestSide, maxSidePx)) { it * 3 / 4 }
        .takeWhile { it >= MIN_POST_IMAGE_SIDE_PX }
        .toList()
        .ifEmpty { listOf(minOf(sourceLongestSide, maxSidePx)) }

private const val MIN_POST_IMAGE_SIDE_PX = 256
