package net.matsudamper.kmp.activitypub.graphql.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GraphQlRequest(
    @SerialName("query")
    val query: String,
    @SerialName("operationName")
    val operationName: String? = null,
    @SerialName("variables")
    val variables: JsonObject? = null,
)
