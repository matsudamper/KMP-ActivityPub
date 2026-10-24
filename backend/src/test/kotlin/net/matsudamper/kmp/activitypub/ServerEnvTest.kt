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
    fun `Android アプリを設定すると署名証明書から origin を足す`() {
        val fingerprint = List(32) { "%02X".format(it) }.joinToString(":")
        val env =
            ServerEnv(
                mapOf(
                    "PASSKEY_RP_ID" to "example.com",
                    "PASSKEY_ANDROID_PACKAGE_NAME" to "com.example.app",
                    "PASSKEY_ANDROID_CERT_SHA256" to fingerprint,
                ),
            )

        assertEquals(
            setOf("https://example.com", "android:apk-key-hash:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"),
            env.passkeyRelyingParty.origins,
        )
    }

    @Test
    fun `PASSKEY_RP_ID が無ければパスキーの設定を読めない`() {
        assertFailsWith<IllegalArgumentException> { ServerEnv(emptyMap()).passkeyRelyingParty }
    }

    @Test
    fun `Android アプリの署名証明書が 2 桁区切りの SHA-256 でなければ起動しない`() {
        val fingerprint = (List(31) { "AB" } + "1FF").joinToString(":")

        assertFailsWith<IllegalArgumentException> {
            ServerEnv(mapOf("PASSKEY_ANDROID_PACKAGE_NAME" to "com.example.app", "PASSKEY_ANDROID_CERT_SHA256" to fingerprint))
        }
    }

    @Test
    fun `Android アプリの署名証明書が無ければ起動しない`() {
        assertFailsWith<IllegalArgumentException> {
            ServerEnv(mapOf("PASSKEY_ANDROID_PACKAGE_NAME" to "com.example.app"))
        }
    }

    @Test
    fun `ENABLE_S3 が未設定なら S3 を使わない`() {
        val env = ServerEnv(S3_REQUIRED - "ENABLE_S3")

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
    fun `S3 を有効にすると既定値で埋める`() {
        val config = assertNotNull(ServerEnv(S3_REQUIRED).s3).imageStorage

        assertNull(config.endpoint)
        assertNull(config.stsEndpoint)
        assertEquals("kmp-activitypub-image-upload", config.roleSessionName)
        assertTrue(config.pathStyleAccess)
    }

    private companion object {
        val S3_REQUIRED: Map<String, String> =
            mapOf(
                "ENABLE_S3" to "true",
                "OIDC_ISSUER" to "https://example.com",
                "OIDC_JWK_PRIVATE" to "{}",
                "S3_BUCKET" to "bucket",
                "S3_REGION" to "ap-northeast-1",
                "S3_ROLE_ARN" to "arn:aws:iam::123456789012:role/image",
                "S3_AUDIENCE" to "sts.amazonaws.com",
            )
    }
}
