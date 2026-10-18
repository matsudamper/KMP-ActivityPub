package net.matsudamper.kmp.activitypub

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.crypto.PasswordHash

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

    @Test
    fun `DOMAIN の scheme と末尾のスラッシュを落とす`() {
        fun domain(raw: String): String? = ServerEnv(mapOf("DOMAIN" to raw)).domain

        assertEquals("example.com", domain("https://example.com"))
        assertEquals("example.com", domain("http://example.com/"))
        assertEquals("example.com", domain(" example.com/ "))
    }

    @Test
    fun `DOMAIN はポート付きのホスト名を受け付ける`() {
        assertEquals("localhost:8080", ServerEnv(mapOf("DOMAIN" to "localhost:8080")).domain)
        val longestLabel = "${"a".repeat(63)}.com"
        assertEquals(longestLabel, ServerEnv(mapOf("DOMAIN" to longestLabel)).domain)
    }

    @Test
    fun `DOMAIN にホスト名以外が付いていたら起動しない`() {
        listOf(
            "https://",
            "example.com/path",
            "https://example.com/path/",
            "example.com?q=1",
            "example.com#top",
            "user@example.com",
            "example.com:port",
            "example..com",
            "example.-com",
            "example-.com",
            ".example.com",
            "${"a".repeat(64)}.com",
            List(64) { "abc" }.joinToString("."),
            "example.com:0",
            "example.com:00080",
            "example.com:65536",
            "example.com:",
        ).forEach { raw ->
            assertFailsWith<IllegalArgumentException>(raw) { ServerEnv(mapOf("DOMAIN" to raw)) }
        }
    }

    @Test
    fun `ADMIN_PASSWORD_HASH が未設定なら null になる`() {
        assertNull(ServerEnv(emptyMap()).adminPasswordHash)
        assertNull(ServerEnv(mapOf("ADMIN_PASSWORD_HASH" to "  ")).adminPasswordHash)
    }

    @Test
    fun `ADMIN_PASSWORD_HASH を読める`() {
        val encoded = PasswordHash.create("とても長いパスワード", iterations = 1_000).encode()

        val hash = assertNotNull(ServerEnv(mapOf("ADMIN_PASSWORD_HASH" to encoded)).adminPasswordHash)

        assertTrue(hash.matches("とても長いパスワード"))
    }

    @Test
    fun `ADMIN_PASSWORD_HASH の形式が違えば起動しない`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(mapOf("ADMIN_PASSWORD_HASH" to "パスワード")) }
    }
}
