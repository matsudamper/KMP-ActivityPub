package net.matsudamper.kmp.activitypub.oidc

/**
 * 検証側は `<issuer>/.well-known/openid-configuration` を取りに来るが、Discovery はルートにしか置かない。
 * パスを含む issuer（例: https://example.com/oidc）だとずれるので、パスなしの issuer を前提にしている
 */
class OidcIssuer(raw: String) {
    val value: String = raw.removeSuffix("/")

    val jwksUri: String = "$value$JWKS_PATH"

    companion object {
        const val DISCOVERY_PATH: String = "/.well-known/openid-configuration"
        const val JWKS_PATH: String = "/jwks"
    }
}
