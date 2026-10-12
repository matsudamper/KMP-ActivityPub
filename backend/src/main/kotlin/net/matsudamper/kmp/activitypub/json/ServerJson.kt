package net.matsudamper.kmp.activitypub.json

import kotlinx.serialization.json.Json

internal object ServerJson {
    /**
     * クライアントが足してくる知らないキー（Apollo の `extensions` など）で落とさない
     */
    val value: Json = Json { ignoreUnknownKeys = true }
}
