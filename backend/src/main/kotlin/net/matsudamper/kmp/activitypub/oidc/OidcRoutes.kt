package net.matsudamper.kmp.activitypub.oidc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kmp.activitypub.OidcEnv
import net.matsudamper.kmp.activitypub.json.respondJson

/**
 * STS などの検証側がトークンの署名を確かめるために読む
 */
internal fun Route.oidcRoutes(
    oidc: OidcEnv,
    signingKey: OidcSigningKey,
) {
    get(OidcIssuer.DISCOVERY_PATH) {
        call.respondJson(
            OidcDiscoveryDocument.serializer(),
            OidcDiscoveryDocument(
                issuer = oidc.issuer.value,
                jwksUri = oidc.issuer.jwksUri,
                responseTypesSupported = listOf("id_token"),
                subjectTypesSupported = listOf("public"),
                idTokenSigningAlgValuesSupported = listOf("RS256"),
            ),
        )
    }
    get(OidcIssuer.JWKS_PATH) {
        call.respondText(
            text = signingKey.publicJwkSetJson,
            contentType = ContentType.Application.Json,
        )
    }
}

@Serializable
private data class OidcDiscoveryDocument(
    @SerialName("issuer")
    val issuer: String,
    @SerialName("jwks_uri")
    val jwksUri: String,
    @SerialName("response_types_supported")
    val responseTypesSupported: List<String>,
    @SerialName("subject_types_supported")
    val subjectTypesSupported: List<String>,
    @SerialName("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: List<String>,
)
