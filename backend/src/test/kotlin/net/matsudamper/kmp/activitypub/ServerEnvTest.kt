package net.matsudamper.kmp.activitypub

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.activitypub.actor.ActorPrivateKey
import net.matsudamper.kmp.activitypub.crypto.PasswordHash

// 環境変数の読み取りを確認する。読むのはここ 1 か所だけなので、
// 変数と既定値の一覧としてもこのテストを見れば分かるようにしておく。
class ServerEnvTest {
    private fun env(vararg values: Pair<String, String>): ServerEnv =
        ServerEnv(values.toMap() + ("DOMAIN" to "example.com"))

    @Test
    fun `DOMAIN 以外は未設定なら既定値になる`() {
        val env = env()

        assertEquals("0.0.0.0", env.host)
        assertEquals(8080, env.port)
        assertEquals(Path.of("./data/kmp-activitypub.db"), env.dbPath)
        assertEquals(ActorPrivateKey.File(Path.of("./data/actor-private-key.pem")), env.actorPrivateKey)
        assertNull(env.staticSrcDir)
    }

    @Test
    fun `環境変数で上書きできる`() {
        val env =
            env(
                "HOST" to "127.0.0.1",
                "PORT" to "9000",
                "DB_PATH" to "/data/rss.db",
                "ACTOR_PRIVATE_KEY_PATH" to "/data/actor.pem",
                "STATIC_SRC_DIR" to "/srv/static",
            )

        assertEquals("127.0.0.1", env.host)
        assertEquals(9000, env.port)
        assertEquals(Path.of("/data/rss.db"), env.dbPath)
        assertEquals(ActorPrivateKey.File(Path.of("/data/actor.pem")), env.actorPrivateKey)
        assertEquals(Path.of("/srv/static"), env.staticSrcDir)
    }

    @Test
    fun `数値でない PORT は既定値に落ちる`() {
        assertEquals(8080, env("PORT" to "ポート").port)
    }

    @Test
    fun `空文字と空白だけの指定は未設定として扱う`() {
        val env =
            env(
                "HOST" to "",
                "PORT" to "",
                "DB_PATH" to " ",
                "STATIC_SRC_DIR" to "  ",
            )

        assertEquals("0.0.0.0", env.host)
        assertEquals(8080, env.port)
        assertEquals(Path.of("./data/kmp-activitypub.db"), env.dbPath)
        assertNull(env.staticSrcDir)
    }

    @Test
    fun `DOMAIN の scheme と末尾のスラッシュを落とす`() {
        fun domain(raw: String): String = ServerEnv(mapOf("DOMAIN" to raw)).domain

        assertEquals("example.com", domain("https://example.com"))
        assertEquals("example.com", domain("http://example.com/"))
        assertEquals("example.com", domain(" example.com/ "))
    }

    // 既定値で起動できてしまうと、間違ったドメインのアクター ID が
    // Mastodon 側にキャッシュされて後から直せない
    @Test
    fun `DOMAIN が未設定なら落ちる`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(emptyMap()) }
        assertFailsWith<IllegalArgumentException> { ServerEnv(mapOf("DOMAIN" to "   ")) }
        assertFailsWith<IllegalArgumentException> { ServerEnv(mapOf("DOMAIN" to "https://")) }
    }

    @Test
    fun `DOMAIN はポート付きのホスト名を受け付ける`() {
        assertEquals("localhost:8080", ServerEnv(mapOf("DOMAIN" to "localhost:8080")).domain)
    }

    @Test
    fun `DOMAIN にホスト名以外が付いていたら落ちる`() {
        listOf(
            "example.com/path",
            "https://example.com/path/",
            "example.com?q=1",
            "example.com#top",
            "user@example.com",
            "example.com:port",
        ).forEach { raw ->
            assertFailsWith<IllegalArgumentException>(raw) { ServerEnv(mapOf("DOMAIN" to raw)) }
        }
    }

    @Test
    fun `鍵の PEM を直接指定できる`() {
        val env = env("ACTOR_PRIVATE_KEY_PEM" to "-----BEGIN PRIVATE KEY-----")

        assertEquals(ActorPrivateKey.Pem("-----BEGIN PRIVATE KEY-----"), env.actorPrivateKey)
    }

    @Test
    fun `鍵の指定が空白だけなら未設定として扱う`() {
        val env = env("ACTOR_PRIVATE_KEY_PEM" to "   ", "ACTOR_PRIVATE_KEY_PATH" to "")

        assertEquals(ActorPrivateKey.File(Path.of("./data/actor-private-key.pem")), env.actorPrivateKey)
    }

    // 片方を黙って無視すると、意図していない鍵で起動したことに気付けない
    @Test
    fun `鍵の PEM とパスの同時指定は落とす`() {
        assertFailsWith<IllegalArgumentException> {
            env(
                "ACTOR_PRIVATE_KEY_PEM" to "-----BEGIN PRIVATE KEY-----",
                "ACTOR_PRIVATE_KEY_PATH" to "/data/actor.pem",
            )
        }
    }

    @Test
    fun `パスワードハッシュは未設定でも起動する`() {
        assertNull(env().adminPasswordHash)
        assertNull(env("ADMIN_PASSWORD_HASH" to "  ").adminPasswordHash)
    }

    @Test
    fun `パスワードハッシュを読める`() {
        val encoded = PasswordHash.create("とても長いパスワード", iterations = 1_000).encode()

        val hash = assertNotNull(env("ADMIN_PASSWORD_HASH" to encoded).adminPasswordHash)

        assertTrue(hash.matches("とても長いパスワード"))
    }

    @Test
    fun `パスワードハッシュの形式が違えば落ちる`() {
        assertFailsWith<IllegalArgumentException> { env("ADMIN_PASSWORD_HASH" to "パスワード") }
    }

    @Test
    fun `Cookie の Secure は既定で付ける`() {
        assertTrue(env().cookieSecure)
        assertTrue(env("COOKIE_SECURE" to " ").cookieSecure)
        assertTrue(env("COOKIE_SECURE" to "TRUE").cookieSecure)
    }

    @Test
    fun `Cookie の Secure を外せる`() {
        assertFalse(env("COOKIE_SECURE" to "false").cookieSecure)
        assertFalse(env("COOKIE_SECURE" to "False").cookieSecure)
    }

    @Test
    fun `Cookie の Secure が true でも false でもなければ落ちる`() {
        assertFailsWith<IllegalArgumentException> { env("COOKIE_SECURE" to "no") }
    }
}
