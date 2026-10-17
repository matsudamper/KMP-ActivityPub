package net.matsudamper.kmp.activitypub

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class ServerEnvTest {
    @Test
    fun `未設定なら既定値になる`() {
        val env = ServerEnv(emptyMap())

        assertEquals("0.0.0.0", env.host)
        assertEquals(8080, env.port)
        assertNull(env.staticSrcDir)
        assertEquals(Path("./data/kmp-activitypub.db"), env.dbPath)
        assertTrue(env.cookieSecure)
        assertNull(env.domain)
        assertFalse(env.trustCfConnectingIp)
    }

    @Test
    fun `空文字と空白だけの指定は未設定として扱う`() {
        val env = ServerEnv(mapOf("HOST" to "", "PORT" to " ", "STATIC_SRC_DIR" to "  ", "DB_PATH" to " "))

        assertEquals("0.0.0.0", env.host)
        assertEquals(8080, env.port)
        assertNull(env.staticSrcDir)
        assertEquals(Path("./data/kmp-activitypub.db"), env.dbPath)
    }

    @Test
    fun `環境変数で上書きできる`() {
        val env =
            ServerEnv(
                mapOf(
                    "HOST" to "127.0.0.1",
                    "PORT" to "9000",
                    "STATIC_SRC_DIR" to "/srv/static",
                    "DB_PATH" to "/srv/db.sqlite",
                    "DOMAIN" to "Example.com",
                ),
            )

        assertEquals("127.0.0.1", env.host)
        assertEquals(9000, env.port)
        assertEquals(Path("/srv/static"), env.staticSrcDir)
        assertEquals(Path("/srv/db.sqlite"), env.dbPath)
        assertEquals("example.com", env.domain)
    }

    @Test
    fun `PORT が数値でなければ起動しない`() {
        assertFailsWith<NumberFormatException> { ServerEnv(mapOf("PORT" to "808O")) }
    }

    @Test
    fun `DB_PATH と COOKIE_SECURE に FALSE を指定するとそのパスと false になる`() {
        val env = ServerEnv(mapOf("DB_PATH" to "/srv/db.sqlite", "COOKIE_SECURE" to "FALSE"))

        assertEquals(Path("/srv/db.sqlite"), env.dbPath)
        assertFalse(env.cookieSecure)
    }

    @Test
    fun `TRUST_CF_CONNECTING_IP に true を指定すると true になる`() {
        assertTrue(ServerEnv(mapOf("TRUST_CF_CONNECTING_IP" to "true")).trustCfConnectingIp)
    }

    @Test
    fun `TRUST_CF_CONNECTING_IP が真偽値でなければ起動しない`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(mapOf("TRUST_CF_CONNECTING_IP" to "yes")) }
    }

    @Test
    fun `COOKIE_SECURE が真偽値でなければ起動しない`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(mapOf("COOKIE_SECURE" to "yes")) }
    }
}
