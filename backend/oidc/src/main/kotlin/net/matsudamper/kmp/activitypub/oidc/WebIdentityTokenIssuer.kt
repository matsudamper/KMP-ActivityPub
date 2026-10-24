package net.matsudamper.kmp.activitypub.oidc

import java.time.Clock
import java.time.Duration
import java.util.Date
import java.util.UUID
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT

/**
 * STS の AssumeRoleWithWebIdentity などに渡す ID トークンを作る
 */
class WebIdentityTokenIssuer(
    private val signingKey: OidcSigningKey,
    val issuer: OidcIssuer,
    private val clock: Clock,
) {
    fun issue(
        subject: String,
        audience: String,
        ttl: Duration,
    ): String {
        val now = clock.instant()
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(signingKey.keyId)
            .build()
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer.value)
            .subject(subject)
            .audience(audience)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(ttl)))
            .jwtID(UUID.randomUUID().toString())
            .build()
        return SignedJWT(header, claims)
            .apply { sign(RSASSASigner(signingKey.privateKey)) }
            .serialize()
    }
}
