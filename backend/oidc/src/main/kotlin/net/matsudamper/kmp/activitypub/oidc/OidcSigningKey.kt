package net.matsudamper.kmp.activitypub.oidc

import java.security.PrivateKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey

/**
 * @param privateJwkJson 秘密鍵を含む RSA の JWK。kid が無いと鍵を差し替えたときに検証側が古い鍵と区別できない
 */
class OidcSigningKey(privateJwkJson: String) {
    private val rsaKey: RSAKey = RSAKey.parse(privateJwkJson)

    init {
        require(rsaKey.isPrivate) { "OIDC_JWK_PRIVATE には秘密鍵を含めること" }
        requireNotNull(rsaKey.keyID) { "OIDC_JWK_PRIVATE には kid を含めること" }
    }

    val keyId: String = rsaKey.keyID

    val publicJwkSetJson: String = JWKSet(rsaKey.toPublicJWK()).toString()

    internal val privateKey: PrivateKey = rsaKey.toPrivateKey()
}
