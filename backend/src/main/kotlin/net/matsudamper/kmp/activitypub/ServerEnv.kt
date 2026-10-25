package net.matsudamper.kmp.activitypub

import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.Path
import net.matsudamper.kmp.activitypub.crypto.PasswordHash
import net.matsudamper.kmp.activitypub.passkey.PasskeyRelyingParty

/**
 * 値は書かれたまま使い、前後の空白も削らない。空のときの扱いは変数ごとに決める
 */
class ServerEnv(
    private val env: Map<String, String> = System.getenv(),
) {
    val host: String = env["HOST"]?.ifEmpty { null } ?: "0.0.0.0"
    val port: Int = env["PORT"]?.ifEmpty { null }?.toInt() ?: 8080

    /**
     * 空を未設定として扱わないと、カレントディレクトリを配信してしまう
     */
    val staticSrcDir: Path? = env["STATIC_SRC_DIR"]?.ifEmpty { null }?.let { Path(it) }
    val dbPath: Path = Path(env["DB_PATH"]?.ifEmpty { null } ?: "./data/kmp-activitypub.db")

    /**
     * `@user@example.com` の example.com。未設定なら他のサーバーから見つけられない
     */
    val domain: String? = env["DOMAIN"]?.ifEmpty { null }?.lowercase()

    /**
     * 未設定なら管理画面にログインできない
     */
    val adminPasswordHash: PasswordHash? = env["ADMIN_PASSWORD_HASH"]?.ifEmpty { null }?.let { PasswordHash.parse(it) }

    /**
     * Valkey を使わないテストでも ServerEnv を作れるよう、読んだときに検査する
     */
    val valkeyUri: String by lazy { checkNotNull(env["VALKEY_URI"]?.ifEmpty { null }) { "VALKEY_URI を指定すること" } }

    /**
     * true のまま http で開くとログインできない
     */
    val cookieSecure: Boolean = booleanOf("COOKIE_SECURE") ?: true

    /**
     * Cloudflare の後ろに置くと接続元が Cloudflare の IP になるので、CF-Connecting-IP を接続元として使う。
     * Cloudflare を通らずに直接届く経路があると、ヘッダーを偽って他人の IP を名乗れる
     */
    val trustCfConnectingIp: Boolean = booleanOf("TRUST_CF_CONNECTING_IP") ?: false

    /**
     * 書き忘れでパスキーが黙って無効にならないよう、true か false を必ず書かせる
     */
    val enablePasskey: Boolean = requireNotNull(booleanOf("ENABLE_PASSKEY")) { "ENABLE_PASSKEY を true か false で設定すること" }

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
     * ENABLE_PASSKEY が true なら PASSKEY_RP_ID は必須。
     * PASSKEY_ORIGINS はカンマ区切りで、無ければ https://<PASSKEY_RP_ID> だけを受け付ける。
     * Android アプリの origin は [passkeyAndroidApp] から足す
     */
    val passkeyRelyingParty: PasskeyRelyingParty? =
        if (enablePasskey) createPasskeyRelyingParty() else null

    private fun createPasskeyRelyingParty(): PasskeyRelyingParty {
        val rpId = requireNotNull(env["PASSKEY_RP_ID"]?.ifEmpty { null }) { "PASSKEY_RP_ID を設定すること" }
        val webOrigins =
            env["PASSKEY_ORIGINS"]
                ?.ifEmpty { null }
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: setOf("https://$rpId")
        return PasskeyRelyingParty(
            id = rpId,
            origins = webOrigins + passkeyAndroidApp?.origins.orEmpty(),
        )
    }

    private fun booleanOf(name: String): Boolean? =
        env[name]?.ifEmpty { null }?.let { raw ->
            raw.lowercase().toBooleanStrictOrNull()
                ?: throw IllegalArgumentException("$name は true か false にすること: $raw")
        }
}

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
