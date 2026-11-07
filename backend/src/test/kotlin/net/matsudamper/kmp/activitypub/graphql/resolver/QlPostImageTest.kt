package net.matsudamper.kmp.activitypub.graphql.resolver

import kotlin.test.Test
import kotlin.test.assertEquals

class QlPostImageTest {
    @Test
    fun クエリがなければサムネイル指定を追加する() {
        assertImageUrls("https://local.example/image.png", "https://local.example/image.png?type=thumbnail")
    }

    @Test
    fun 既存のクエリを保持してサムネイル指定を追加する() {
        assertImageUrls("https://remote.example/image?token=a%2Fb", "https://remote.example/image?token=a%2Fb&type=thumbnail")
    }

    @Test
    fun フラグメントの前にサムネイル指定を追加する() {
        assertImageUrls("https://remote.example/image.gif#frame", "https://remote.example/image.gif?type=thumbnail#frame")
    }

    @Test
    fun クエリとフラグメントを保持してサムネイル指定を追加する() {
        assertImageUrls("https://remote.example/image.webp?token=a%2Fb#frame", "https://remote.example/image.webp?token=a%2Fb&type=thumbnail#frame")
    }

    private fun assertImageUrls(url: String, thumbnailUrl: String) {
        val image = createQlPostImage(url = url, description = "画像の説明")
        assertEquals(url, image.url)
        assertEquals(thumbnailUrl, image.thumbnailUrl)
        assertEquals("画像の説明", image.description)
    }
}
