package net.matsudamper.kmp.activitypub.federation.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class WebFingerResponse(
    @SerialName("subject")
    val subject: String,
    @SerialName("aliases")
    val aliases: List<String>,
    @SerialName("links")
    val links: List<Link>,
) {
    @Serializable
    data class Link(
        @SerialName("rel")
        val rel: String,
        @SerialName("type")
        val type: String,
        @SerialName("href")
        val href: String,
    )
}
