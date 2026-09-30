package net.matsudamper.kmp.activitypub.graphql.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GraphQlBadRequest(
    @SerialName("message")
    val message: String,
)
