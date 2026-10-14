package net.matsudamper.kmp.activitypub.json

import kotlinx.serialization.SerializationStrategy
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText

/**
 * Ktor の ContentNegotiation は serializer をリフレクションで引き、native-image で 500 になる
 */
internal suspend fun <T> ApplicationCall.respondJson(
    serializer: SerializationStrategy<T>,
    value: T,
    contentType: ContentType = ContentType.Application.Json,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respondText(
        text = ServerJson.value.encodeToString(serializer, value),
        contentType = contentType,
        status = status,
    )
}
