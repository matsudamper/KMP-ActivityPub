package net.matsudamper.kmp.activitypub.oidc

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.SignedJWT

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class WebIdentityTokenIssuerTest {
    private val privateJwk: RSAKey =
        RSAKeyGenerator(2048)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyID("test-kid")
            .generate()
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `公開している JWK で検証できるトークンを作る`() {
        val signingKey = OidcSigningKey(privateJwk.toJSONString())
        val issuer = WebIdentityTokenIssuer(
            signingKey = signingKey,
            issuer = OidcIssuer("https://example.com/"),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        val token = SignedJWT.parse(issuer.issue(subject = "1", name = "1", audience = "sts.amazonaws.com", ttl = Duration.ofMinutes(5)))

        val publicJwk = JWKSet.parse(signingKey.publicJwkSetJson).getKeyByKeyId("test-kid") as RSAKey
        assertTrue(token.verify(RSASSAVerifier(publicJwk)))
        assertEquals("test-kid", token.header.keyID)
        assertEquals("https://example.com", token.jwtClaimsSet.issuer)
        assertEquals("1", token.jwtClaimsSet.subject)
        assertEquals(listOf("sts.amazonaws.com"), token.jwtClaimsSet.audience)
        assertEquals(now.plus(Duration.ofMinutes(5)), token.jwtClaimsSet.expirationTime.toInstant())
    }

    @Test
    fun `公開する JWK に秘密鍵を含めない`() {
        val signingKey = OidcSigningKey(privateJwk.toJSONString())

        assertTrue(JWKSet.parse(signingKey.publicJwkSetJson).keys.none { it.isPrivate })
    }

    @Test
    fun `公開鍵だけの JWK は受け付けない`() {
        assertFailsWith<IllegalArgumentException> {
            OidcSigningKey(privateJwk.toPublicJWK().toJSONString())
        }
    }
}
