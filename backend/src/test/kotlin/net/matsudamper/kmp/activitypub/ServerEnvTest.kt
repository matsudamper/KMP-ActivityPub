package net.matsudamper.kmp.activitypub

import java.net.URI
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
        val env = ServerEnv(FEATURES_DISABLED)

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
        val env = ServerEnv(FEATURES_DISABLED + mapOf("HOST" to "", "PORT" to "", "STATIC_SRC_DIR" to "", "DB_PATH" to ""))

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
        val env = ServerEnv(FEATURES_DISABLED + mapOf("STATIC_SRC_DIR" to " /srv/static ", "DOMAIN" to " example.com"))

        assertEquals(Path(" /srv/static "), env.staticSrcDir)
        assertEquals(" example.com", env.domain)
    }

    @Test
    fun `環境変数で上書きできる`() {
        val env =
            ServerEnv(
                FEATURES_DISABLED +
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
        assertFailsWith<NumberFormatException> { ServerEnv(FEATURES_DISABLED + mapOf("PORT" to "808O")) }
    }

    @Test
    fun `DB_PATH と COOKIE_SECURE に FALSE を指定するとそのパスと false になる`() {
        val env = ServerEnv(FEATURES_DISABLED + mapOf("DB_PATH" to "/srv/db.sqlite", "COOKIE_SECURE" to "FALSE"))

        assertEquals(Path("/srv/db.sqlite"), env.dbPath)
        assertFalse(env.cookieSecure)
    }

    @Test
    fun `TRUST_CF_CONNECTING_IP に true を指定すると true になる`() {
        assertTrue(ServerEnv(FEATURES_DISABLED + mapOf("TRUST_CF_CONNECTING_IP" to "true")).trustCfConnectingIp)
    }

    @Test
    fun `TRUST_CF_CONNECTING_IP が真偽値でなければ起動しない`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(FEATURES_DISABLED + mapOf("TRUST_CF_CONNECTING_IP" to "yes")) }
    }

    @Test
    fun `COOKIE_SECURE が真偽値でなければ起動しない`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(FEATURES_DISABLED + mapOf("COOKIE_SECURE" to "yes")) }
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
    fun `REMOTE_ICON_PROXY_SECRET が無ければ起動しない`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(FEATURES_DISABLED - "REMOTE_ICON_PROXY_SECRET") }
        assertFailsWith<IllegalArgumentException> { ServerEnv(FEATURES_DISABLED + mapOf("REMOTE_ICON_PROXY_SECRET" to "")) }
    }

    @Test
    fun `ENABLE_PASSKEY が無ければ起動しない`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(FEATURES_DISABLED - "ENABLE_PASSKEY") }
    }

    @Test
    fun `ENABLE_PASSKEY が true で PASSKEY_RP_ID が無ければ起動しない`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(FEATURES_DISABLED + mapOf("ENABLE_PASSKEY" to "true")) }
    }

    @Test
    fun `ENABLE_PASSKEY が false ならパスキーを使わない`() {
        assertNull(ServerEnv(FEATURES_DISABLED).passkeyRelyingParty)
    }

    @Test
    fun `Android アプリの署名証明書が 2 桁区切りの SHA-256 でなければ起動しない`() {
        val fingerprint = (List(31) { "AB" } + "1FF").joinToString(":")

        assertFailsWith<IllegalArgumentException> {
            ServerEnv(FEATURES_DISABLED + mapOf("PASSKEY_ANDROID_PACKAGE_NAME" to "com.example.app", "PASSKEY_ANDROID_CERT_SHA256" to fingerprint))
        }
    }

    @Test
    fun `Android アプリの署名証明書が無ければ起動しない`() {
        assertFailsWith<IllegalArgumentException> {
            ServerEnv(FEATURES_DISABLED + mapOf("PASSKEY_ANDROID_PACKAGE_NAME" to "com.example.app"))
        }
    }

    @Test
    fun `ENABLE_S3 が無ければ起動しない`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(FEATURES_DISABLED - "ENABLE_S3") }
    }

    @Test
    fun `ENABLE_S3 が false なら S3 を使わない`() {
        val env = ServerEnv(FEATURES_DISABLED)

        assertFalse(env.enableS3)
        assertNull(env.s3)
    }

    @Test
    fun `ENABLE_S3 が true で OIDC が無ければ起動しない`() {
        assertFailsWith<IllegalArgumentException> {
            ServerEnv(S3_REQUIRED - "OIDC_JWK_PRIVATE")
        }
    }

    @Test
    fun `ENABLE_S3 が true で S3_ENDPOINT が無ければ起動しない`() {
        assertFailsWith<IllegalArgumentException> {
            ServerEnv(S3_REQUIRED - "S3_ENDPOINT")
        }
    }

    @Test
    fun `ENABLE_S3 が true で S3_ENDPOINT が空なら起動しない`() {
        assertFailsWith<IllegalArgumentException> {
            ServerEnv(S3_REQUIRED + mapOf("S3_ENDPOINT" to ""))
        }
    }

    @Test
    fun `S3 を有効にすると既定値で埋める`() {
        val config = assertNotNull(ServerEnv(S3_REQUIRED).s3).imageStorage

        assertEquals(URI("https://s3.example.com"), config.endpoint)
        assertEquals(URI("https://sts.example.com"), config.stsEndpoint)
        assertEquals("kmp-activitypub-image-upload", config.roleSessionName)
        assertTrue(config.pathStyleAccess)
    }

    private companion object {
        val FEATURES_DISABLED: Map<String, String> = mapOf("ENABLE_PASSKEY" to "false", "ENABLE_S3" to "false", "REMOTE_ICON_PROXY_SECRET" to "test-secret")
        val PASSKEY_ENABLED: Map<String, String> = FEATURES_DISABLED + mapOf("ENABLE_PASSKEY" to "true", "PASSKEY_RP_ID" to "example.com")
        val S3_REQUIRED: Map<String, String> =
            FEATURES_DISABLED +
                mapOf(
                    "ENABLE_S3" to "true",
                    "OIDC_ISSUER" to "https://example.com",
                    "OIDC_JWK_PRIVATE" to "{}",
                    "S3_BUCKET" to "bucket",
                    "S3_REGION" to "ap-northeast-1",
                    "S3_ROLE_ARN" to "arn:aws:iam::123456789012:role/image",
                    "S3_AUDIENCE" to "sts.amazonaws.com",
                    "S3_ENDPOINT" to "https://s3.example.com",
                    "STS_ENDPOINT" to "https://sts.example.com",
                )
    }
}
