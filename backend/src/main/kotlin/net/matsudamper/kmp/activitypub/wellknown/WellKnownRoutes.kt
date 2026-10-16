package net.matsudamper.kmp.activitypub.wellknown

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kmp.activitypub.PasskeyAndroidApp
import net.matsudamper.kmp.activitypub.json.respondJson

internal fun Route.wellKnownRoutes(passkeyAndroidApp: PasskeyAndroidApp?) {
    // Digital Asset Links。Android の Credential Manager は、RP ID のドメインがこのアプリを
    // get_login_creds で許可していなければパスキーを作らせない
    get("/.well-known/assetlinks.json") {
        if (passkeyAndroidApp == null) {
            call.respondText("PASSKEY_ANDROID_PACKAGE_NAME が未設定", status = HttpStatusCode.NotFound)
            return@get
        }
        call.respondJson(
            ListSerializer(AssetLink.serializer()),
            listOf(
                AssetLink(
                    relation = listOf("delegate_permission/common.get_login_creds"),
                    target =
                    AssetLink.Target(
                        namespace = "android_app",
                        packageName = passkeyAndroidApp.packageName,
                        sha256CertFingerprints = passkeyAndroidApp.certSha256Fingerprints,
                    ),
                ),
            ),
        )
    }
}

@Serializable
private data class AssetLink(
    @SerialName("relation")
    val relation: List<String>,
    @SerialName("target")
    val target: Target,
) {
    @Serializable
    data class Target(
        @SerialName("namespace")
        val namespace: String,
        @SerialName("package_name")
        val packageName: String,
        @SerialName("sha256_cert_fingerprints")
        val sha256CertFingerprints: List<String>,
    )
}
