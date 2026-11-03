package net.matsudamper.kmp.activitypub.frontend.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class PostImageSidesTest {
    @Test
    fun largeImageStartsAtMaxSideAndShrinksByQuarters() {
        assertEquals(listOf(1920, 1440, 1080, 810, 607, 455, 341), postImageSideCandidates(sourceLongestSide = 4000, maxSidePx = 1920))
    }

    @Test
    fun smallImageIsNotEnlarged() {
        assertEquals(listOf(1000, 750, 562, 421, 315), postImageSideCandidates(sourceLongestSide = 1000, maxSidePx = 1920))
    }

    @Test
    fun tinyImageKeepsItsOwnSize() {
        assertEquals(listOf(100), postImageSideCandidates(sourceLongestSide = 100, maxSidePx = 1920))
    }
}
