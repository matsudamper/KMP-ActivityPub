package net.matsudamper.kmp.activitypub

import java.net.URI
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.Path
import net.matsudamper.kmp.activitypub.imagestorage.s3.S3ImageStorageConfig
import net.matsudamper.kmp.activitypub.oidc.OidcIssuer
import net.matsudamper.kmp.activitypub.passkey.PasskeyRelyingParty
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 値は書かれたまま使い、前後の空白も削らない。空のときの扱いは変数ごとに決める
 */
class ServerEnv(
    private val env: Map<String, String> = System.getenv(),
) {
    val host: String = required("HOST")
    val port: Int = required("PORT").toInt()

    /**
     * 空を未設定として扱わないと、カレントディレクトリを配信してしまう
     */
    val staticSrcDir: Path? = env["STATIC_SRC_DIR"]?.ifEmpty { null }?.let { Path(it) }
    val dbPath: Path = Path(required("DB_PATH"))

    /**
     * `@user@example.com` の example.com。未設定なら他のサーバーから見つけられない
     */
    val domain: String? = env["DOMAIN"]?.ifEmpty { null }?.lowercase()

    /**
     * Valkey を使わないテストでも ServerEnv を作れるよう、読んだときに検査する
     */
    val valkeyUri: String by lazy { checkNotNull(env["VALKEY_URI"]?.ifEmpty { null }) { "VALKEY_URI を指定すること" } }

    /**
     * true のまま http で開くとログインできない
     */
    val cookieSecure: Boolean = requiredBoolean("COOKIE_SECURE")

    /**
     * Cloudflare の後ろに置くと接続元が Cloudflare の IP になるので、CF-Connecting-IP を接続元として使う。
     * Cloudflare を通らずに直接届く経路があると、ヘッダーを偽って他人の IP を名乗れる
     */
    val trustCfConnectingIp: Boolean = requiredBoolean("TRUST_CF_CONNECTING_IP")

    /**
     * 他のサーバーのプロフィール画像を中継する URL の署名に使う。署名が無いと、誰でも任意の URL をこのサーバーに取りに行かせられる。
     * 変えると中継 URL が全部変わり、Cloudflare のキャッシュに当たらなくなる
     */
    val remoteIconProxySecret: String = required("REMOTE_ICON_PROXY_SECRET")

    /**
     * 書き忘れでパスキーが黙って無効にならないよう、true か false を必ず書かせる
     */
    val enablePasskey: Boolean = requiredBoolean("ENABLE_PASSKEY")

    /**
     * Android アプリのパスキーを受け付けるときに設定する。
     * PASSKEY_ANDROID_CERT_SHA256 は署名証明書の SHA-256 を `AA:BB:...` の形で、カンマ区切りで複数書ける
     */
    val passkeyAndroidApp: PasskeyAndroidApp? =
        env["PASSKEY_ANDROID_PACKAGE_NAME"]?.ifEmpty { null }?.let { packageName ->
            PasskeyAndroidApp(
                packageName = packageName,
                certSha256Fingerprints =
                env["PASSKEY_ANDROID_CERT_SHA256"]
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                    .ifEmpty { throw IllegalArgumentException("PASSKEY_ANDROID_PACKAGE_NAME を設定するなら PASSKEY_ANDROID_CERT_SHA256 も設定すること") },
            )
        }

    /**
     * ENABLE_PASSKEY が true なら PASSKEY_RP_ID と PASSKEY_ORIGINS は必須。
     * PASSKEY_ORIGINS はカンマ区切りで書く。
     * Android アプリの origin は [passkeyAndroidApp] から足す
     */
    val passkeyRelyingParty: PasskeyRelyingParty? =
        if (enablePasskey) createPasskeyRelyingParty() else null

    /**
     * 管理画面に入れるユーザー。UserInfo.id の値をカンマ区切りで書く。未設定なら誰も入れない
     */
    val adminAccountIds: Set<AccountId> =
        env["ADMIN_USER_IDS"]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.map { raw ->
                AccountId(requireNotNull(raw.toLongOrNull()) { "ADMIN_USER_IDS は数字をカンマ区切りで書くこと: $raw" })
            }
            ?.toSet()
            .orEmpty()

    val enableS3: Boolean = requiredBoolean("ENABLE_S3")

    /**
     * 画像を S3 に置く。認証情報は、このサーバーを OIDC の IdP にして発行したトークンを STS に渡して受け取る。
     * OIDC_JWK_PRIVATE は RSA の秘密鍵を JWK の JSON で書く。`./gradlew :backend:oidc:generateSigningJwk` で作れる。
     */
    val s3: S3Env? =
        if (enableS3) {
            S3Env(
                oidc = OidcEnv(
                    issuer = OidcIssuer(required("OIDC_ISSUER")),
                    privateJwkJson = required("OIDC_JWK_PRIVATE"),
                ),
                imageStorage = S3ImageStorageConfig(
                    endpoint = URI(required("S3_ENDPOINT")),
                    stsEndpoint = URI(required("STS_ENDPOINT")),
                    region = required("S3_REGION"),
                    bucket = required("S3_BUCKET"),
                    roleArn = required("S3_ROLE_ARN"),
                    roleSessionName = required("S3_ROLE_SESSION_NAME"),
                    audience = required("S3_AUDIENCE"),
                    pathStyleAccess = requiredBoolean("S3_PATH_STYLE_ACCESS"),
                ),
            )
        } else {
            null
        }

    private fun createPasskeyRelyingParty(): PasskeyRelyingParty {
        val rpId = required("PASSKEY_RP_ID")
        val webOrigins =
            required("PASSKEY_ORIGINS")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
                .ifEmpty { throw IllegalArgumentException("PASSKEY_ORIGINS を設定すること") }
        return PasskeyRelyingParty(
            id = rpId,
            origins = webOrigins + passkeyAndroidApp?.origins.orEmpty(),
        )
    }

    private fun required(name: String): String =
        requireNotNull(env[name]?.ifEmpty { null }) { "$name を設定すること" }

    private fun requiredBoolean(name: String): Boolean {
        val raw = required(name)
        return raw.lowercase().toBooleanStrictOrNull()
            ?: throw IllegalArgumentException("$name は true か false にすること: $raw")
    }
}

class S3Env(
    val oidc: OidcEnv,
    val imageStorage: S3ImageStorageConfig,
)

class OidcEnv(
    val issuer: OidcIssuer,
    val privateJwkJson: String,
)

/**
 * @param certSha256Fingerprints `AA:BB:...` の形
 */
class PasskeyAndroidApp(
    val packageName: String,
    val certSha256Fingerprints: List<String>,
) {
    /**
     * Android の Credential Manager は clientDataJSON の origin に
     * `android:apk-key-hash:<署名証明書の SHA-256 の base64url>` を入れる。
     * 書き間違えると assetlinks.json と origin が実際の証明書と食い違うので、作るときに形を確かめる
     */
    val origins: Set<String> =
        certSha256Fingerprints
            .map { fingerprint ->
                require(SHA256_FINGERPRINT.matches(fingerprint)) { "PASSKEY_ANDROID_CERT_SHA256 は AA:BB:... の形の SHA-256 にすること: $fingerprint" }
                val bytes = fingerprint.split(':').map { it.toInt(16).toByte() }.toByteArray()
                "android:apk-key-hash:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            }.toSet()

    private companion object {
        val SHA256_FINGERPRINT = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){31}$")
    }
}
