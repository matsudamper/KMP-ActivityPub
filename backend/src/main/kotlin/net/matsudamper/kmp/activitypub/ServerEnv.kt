package net.matsudamper.kmp.activitypub

import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.Path
import net.matsudamper.kmp.activitypub.passkey.PasskeyRelyingParty

/**
 * 空白だけの値は未設定として扱う。STATIC_SRC_DIR が空だとカレントディレクトリを配信してしまう
 */
class ServerEnv(
    env: Map<String, String> = System.getenv(),
) {
    private val values = env.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }

    val host: String = values["HOST"] ?: "0.0.0.0"
    val port: Int = values["PORT"]?.toInt() ?: 8080
    val staticSrcDir: Path? = values["STATIC_SRC_DIR"]?.let { Path(it) }
    val dbPath: Path = Path(values["DB_PATH"] ?: "./data/kmp-activitypub.db")

    /**
     * Valkey を使わないテストでも ServerEnv を作れるよう、読んだときに検査する
     */
    val valkeyUri: String by lazy { checkNotNull(values["VALKEY_URI"]) { "VALKEY_URI を指定すること" } }

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
     * Android アプリのパスキーを受け付けるときに設定する。
     * PASSKEY_ANDROID_CERT_SHA256 は署名証明書の SHA-256 を `AA:BB:...` の形で、カンマ区切りで複数書ける
     */
    val passkeyAndroidApp: PasskeyAndroidApp? =
        values["PASSKEY_ANDROID_PACKAGE_NAME"]?.let { packageName ->
            PasskeyAndroidApp(
                packageName = packageName,
                certSha256Fingerprints =
                values["PASSKEY_ANDROID_CERT_SHA256"]
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                    .ifEmpty { throw IllegalArgumentException("PASSKEY_ANDROID_PACKAGE_NAME を設定するなら PASSKEY_ANDROID_CERT_SHA256 も設定すること") },
            )
        }

    /**
     * PASSKEY_RP_ID は必須。読むのはパスキーの処理を組み立てるときなので、未設定ならそこで起動が止まる。
     * PASSKEY_ORIGINS はカンマ区切りで、無ければ https://<PASSKEY_RP_ID> だけを受け付ける。
     * Android アプリの origin は [passkeyAndroidApp] から足す
     */
    val passkeyRelyingParty: PasskeyRelyingParty by lazy {
        val rpId = requireNotNull(values["PASSKEY_RP_ID"]) { "PASSKEY_RP_ID を設定すること" }
        val webOrigins =
            values["PASSKEY_ORIGINS"]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: setOf("https://$rpId")
        PasskeyRelyingParty(
            id = rpId,
            origins = webOrigins + passkeyAndroidApp?.origins.orEmpty(),
        )
    }

    private fun booleanOf(name: String): Boolean? =
        values[name]?.let { raw ->
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
