package net.matsudamper.kmp.activitypub.oidc

import java.net.URI

/**
 * 検証側は `<issuer>/.well-known/openid-configuration` を取りに来る。
 * Discovery はルートにしか置かないので、パスを含む issuer は受け付けない
 */
class OidcIssuer(raw: String) {
    val value: String = raw.removeSuffix("/")

    init {
        val uri = URI(value)
        require(uri.scheme == "https" || uri.scheme == "http") { "OIDC_ISSUER は http か https の URL にすること: $raw" }
        require(uri.host != null) { "OIDC_ISSUER にホストを含めること: $raw" }
        require(uri.rawPath.isNullOrEmpty()) { "OIDC_ISSUER にパスを含めないこと: $raw" }
        require(uri.rawQuery == null && uri.rawFragment == null) { "OIDC_ISSUER にクエリやフラグメントを含めないこと: $raw" }
    }

    val jwksUri: String = "$value$JWKS_PATH"

    companion object {
        const val DISCOVERY_PATH: String = "/.well-known/openid-configuration"
        const val JWKS_PATH: String = "/.well-known/jwks.json"
    }
}
