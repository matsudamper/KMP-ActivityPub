package net.matsudamper.kmp.activitypub.oidc

import java.util.UUID
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator

object SigningJwkGeneratorCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val jwk = RSAKeyGenerator(2048)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyID(UUID.randomUUID().toString())
            .generate()
        println(jwk.toJSONString())
    }
}
