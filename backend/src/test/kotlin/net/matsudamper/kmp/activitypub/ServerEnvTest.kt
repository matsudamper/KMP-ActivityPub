package net.matsudamper.kmp.activitypub

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class ServerEnvTest {
    @Test
    fun `未設定なら既定値になる`() {
        val env = ServerEnv(PASSKEY_DISABLED)

        assertEquals("0.0.0.0", env.host)
        assertEquals(8080, env.port)
        assertNull(env.staticSrcDir)
        assertEquals(Path("./data/kmp-activitypub.db"), env.dbPath)
        assertTrue(env.cookieSecure)
        assertNull(env.domain)
        assertFalse(env.trustCfConnectingIp)
    }

    @Test
    fun `空文字の指定は未設定として扱う`() {
        val env = ServerEnv(PASSKEY_DISABLED + mapOf("HOST" to "", "PORT" to "", "STATIC_SRC_DIR" to "", "DB_PATH" to ""))

        assertEquals("0.0.0.0", env.host)
        assertEquals(8080, env.port)
        assertNull(env.staticSrcDir)
        assertEquals(Path("./data/kmp-activitypub.db"), env.dbPath)
    }

    @Test
    fun `PASSKEY_ORIGINS が空文字なら https の RP ID を受け付ける`() {
        val env = ServerEnv(PASSKEY_ENABLED + mapOf("PASSKEY_ORIGINS" to ""))

        assertEquals(setOf("https://example.com"), assertNotNull(env.passkeyRelyingParty).origins)
    }

    @Test
    fun `前後の空白は削らずにそのまま使う`() {
        val env = ServerEnv(PASSKEY_DISABLED + mapOf("STATIC_SRC_DIR" to " /srv/static ", "DOMAIN" to " example.com"))

        assertEquals(Path(" /srv/static "), env.staticSrcDir)
        assertEquals(" example.com", env.domain)
    }

    @Test
    fun `環境変数で上書きできる`() {
        val env =
            ServerEnv(
                PASSKEY_DISABLED +
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
        assertFailsWith<NumberFormatException> { ServerEnv(PASSKEY_DISABLED + mapOf("PORT" to "808O")) }
    }

    @Test
    fun `DB_PATH と COOKIE_SECURE に FALSE を指定するとそのパスと false になる`() {
        val env = ServerEnv(PASSKEY_DISABLED + mapOf("DB_PATH" to "/srv/db.sqlite", "COOKIE_SECURE" to "FALSE"))

        assertEquals(Path("/srv/db.sqlite"), env.dbPath)
        assertFalse(env.cookieSecure)
    }

    @Test
    fun `TRUST_CF_CONNECTING_IP に true を指定すると true になる`() {
        assertTrue(ServerEnv(PASSKEY_DISABLED + mapOf("TRUST_CF_CONNECTING_IP" to "true")).trustCfConnectingIp)
    }

    @Test
    fun `TRUST_CF_CONNECTING_IP が真偽値でなければ起動しない`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(PASSKEY_DISABLED + mapOf("TRUST_CF_CONNECTING_IP" to "yes")) }
    }

    @Test
    fun `COOKIE_SECURE が真偽値でなければ起動しない`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(PASSKEY_DISABLED + mapOf("COOKIE_SECURE" to "yes")) }
    }

    @Test
    fun `Android アプリを設定すると署名証明書から origin を足す`() {
        val fingerprint = List(32) { "%02X".format(it) }.joinToString(":")
        val env =
            ServerEnv(
                PASSKEY_ENABLED +
                    mapOf(
                        "PASSKEY_ANDROID_PACKAGE_NAME" to "com.example.app",
                        "PASSKEY_ANDROID_CERT_SHA256" to fingerprint,
                    ),
            )

        assertEquals(
            setOf("https://example.com", "android:apk-key-hash:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"),
            assertNotNull(env.passkeyRelyingParty).origins,
        )
    }

    @Test
    fun `ENABLE_PASSKEY が無ければ起動しない`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(emptyMap()) }
    }

    @Test
    fun `ENABLE_PASSKEY が true で PASSKEY_RP_ID が無ければ起動しない`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(mapOf("ENABLE_PASSKEY" to "true")) }
    }

    @Test
    fun `ENABLE_PASSKEY が false ならパスキーを使わない`() {
        assertNull(ServerEnv(PASSKEY_DISABLED).passkeyRelyingParty)
    }

    @Test
    fun `Android アプリの署名証明書が 2 桁区切りの SHA-256 でなければ起動しない`() {
        val fingerprint = (List(31) { "AB" } + "1FF").joinToString(":")

        assertFailsWith<IllegalArgumentException> {
            ServerEnv(PASSKEY_DISABLED + mapOf("PASSKEY_ANDROID_PACKAGE_NAME" to "com.example.app", "PASSKEY_ANDROID_CERT_SHA256" to fingerprint))
        }
    }

    @Test
    fun `Android アプリの署名証明書が無ければ起動しない`() {
        assertFailsWith<IllegalArgumentException> {
            ServerEnv(PASSKEY_DISABLED + mapOf("PASSKEY_ANDROID_PACKAGE_NAME" to "com.example.app"))
        }
    }

    private companion object {
        val PASSKEY_DISABLED: Map<String, String> = mapOf("ENABLE_PASSKEY" to "false")
        val PASSKEY_ENABLED: Map<String, String> = mapOf("ENABLE_PASSKEY" to "true", "PASSKEY_RP_ID" to "example.com")
    }
}
