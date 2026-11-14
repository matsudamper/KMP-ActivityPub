package net.matsudamper.kmp.activitypub.frontend.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class PathPatternTest {
    @Test
    fun 可変部分の無いパターンは同じパスだけに合う() {
        val pattern = PathPattern("/home")

        assertEquals(mapOf(), pattern.match("/home"))
        assertNull(pattern.match("/home/"))
        assertNull(pattern.match("/homes"))
    }

    @Test
    fun 可変部分の値を名前と組で返す() {
        val pattern = PathPattern("/@{username}")

        assertEquals(mapOf("username" to "john.doe"), pattern.match("/@john.doe"))
    }

    @Test
    fun 可変部分は空や複数階層に合わない() {
        val pattern = PathPattern("/@{username}")

        assertNull(pattern.match("/@"))
        assertNull(pattern.match("/@alice/1"))
        assertNull(pattern.match("/alice"))
    }

    @Test
    fun 固定部分の正規表現の記号はそのままの文字として扱う() {
        val pattern = PathPattern("/a.b/{id}")

        assertEquals(mapOf("id" to "1"), pattern.match("/a.b/1"))
        assertNull(pattern.match("/axb/1"))
    }

    @Test
    fun 可変部分に値を入れてパスを組み立てる() {
        val pattern = PathPattern("/@{username}")

        assertEquals("/@alice", pattern.pathOf(mapOf("username" to "alice")))
    }

    @Test
    fun 区切りの文字が入った値も1階層に入れて戻せる() {
        val pattern = PathPattern("/posts/{postId}")
        val postId = "uhttps://remote.example/notes/1?a=b#c%d"

        val path = pattern.pathOf(mapOf("postId" to postId))

        assertEquals("/posts/uhttps:%2F%2Fremote.example%2Fnotes%2F1%3Fa=b%23c%25d", path)
        assertEquals(mapOf("postId" to postId), pattern.match(path))
    }

    @Test
    fun ブラウザがエンコードした日本語を戻す() {
        val pattern = PathPattern("/@{username}")

        assertEquals(mapOf("username" to "あ"), pattern.match("/@%E3%81%82"))
    }
}
