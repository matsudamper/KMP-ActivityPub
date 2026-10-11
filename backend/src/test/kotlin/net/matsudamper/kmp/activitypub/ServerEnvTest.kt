package net.matsudamper.kmp.activitypub

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerEnvTest {
    @Test
    fun `未設定なら既定値になる`() {
        val env = ServerEnv(emptyMap())

        assertEquals("0.0.0.0", env.host)
        assertEquals(8080, env.port)
        assertNull(env.staticSrcDir)
    }

    @Test
    fun `空文字と空白だけの指定は未設定として扱う`() {
        val env = ServerEnv(mapOf("HOST" to "", "PORT" to " ", "STATIC_SRC_DIR" to "  "))

        assertEquals("0.0.0.0", env.host)
        assertEquals(8080, env.port)
        assertNull(env.staticSrcDir)
    }

    @Test
    fun `環境変数で上書きできる`() {
        val env = ServerEnv(mapOf("HOST" to "127.0.0.1", "PORT" to "9000", "STATIC_SRC_DIR" to "/srv/static"))

        assertEquals("127.0.0.1", env.host)
        assertEquals(9000, env.port)
        assertEquals(Path("/srv/static"), env.staticSrcDir)
    }
}
