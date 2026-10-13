package net.matsudamper.kmp.activitypub.json

import kotlinx.serialization.json.Json

internal object ServerJson {
    /**
     * Apollo の `extensions` などで落とさない
     */
    val value: Json = Json { ignoreUnknownKeys = true }
}
