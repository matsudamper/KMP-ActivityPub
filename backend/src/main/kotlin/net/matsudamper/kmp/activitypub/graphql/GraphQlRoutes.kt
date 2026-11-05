package net.matsudamper.kmp.activitypub.graphql

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readBuffer
import net.matsudamper.kmp.activitypub.graphql.data.GraphQlBadRequest
import net.matsudamper.kmp.activitypub.graphql.data.GraphQlRequest
import net.matsudamper.kmp.activitypub.json.ServerJson
import net.matsudamper.kmp.activitypub.json.respondJson
import net.matsudamper.kmp.activitypub.logic.NotePostService
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext

internal const val GRAPHQL_PATH = "/graphql"

private const val UPLOAD_DIRECTORY_PREFIX = "graphql-upload"

/**
 * プロフィール画像の上限 2MiB を base64 にした分が入る大きさ
 */
private const val MAX_BODY_BYTES = 3 * 1024 * 1024

/**
 * 投稿の画像の上限に、operations と map と multipart の区切りが入る分を足した大きさ
 */
private const val MAX_MULTIPART_BODY_BYTES = NotePostService.MAX_IMAGE_BYTES + 1024 * 1024

/**
 * Apollo Kotlin の multipart に付けさせるヘッダー。multipart/form-data はプリフライト無しで別オリジンから送れるので、
 * 単純でないヘッダーを求めてプリフライトを起こさせる
 */
private const val REQUIRE_PREFLIGHT_HEADER = "Apollo-Require-Preflight"

/**
 * multipart はログインを確かめる前に画像を一時ファイルに書くので、未ログインでも同時に送られるとディスクを使い切られる。
 * 受け取ってから応答するまでの数をこれで抑える
 */
private const val MAX_CONCURRENT_MULTIPART_REQUESTS = 4

internal fun Route.graphQlRoutes(engine: GraphQlEngine) {
    val multipartPermits = Semaphore(MAX_CONCURRENT_MULTIPART_REQUESTS)
    route(GRAPHQL_PATH) {
        // chunked では Content-Length の判定をすり抜ける。上限を超えた後も読み続けると、multipart の同時に受ける枠を塞がれるので、受信そのものを打ち切る
        install(RequestBodyLimit) {
            bodyLimit { call ->
                if (call.request.contentType().match(ContentType.MultiPart.FormData)) MAX_MULTIPART_BODY_BYTES.toLong() else MAX_BODY_BYTES.toLong()
            }
        }
        graphQlPost(engine = engine, multipartPermits = multipartPermits)
    }
}

private fun Route.graphQlPost(
    engine: GraphQlEngine,
    multipartPermits: Semaphore,
) {
    post {
        val contentType = call.request.contentType()
        when {
            contentType.match(ContentType.Application.Json) -> respond(call, engine, receiveJson(call))

            contentType.match(ContentType.MultiPart.FormData) -> multipartPermits.withPermit { respondMultipart(call, engine) }

            // text/plain などはプリフライト無しで別オリジンから送れる
            else -> respond(
                call,
                engine,
                ReceivedGraphQlRequest.Rejected(
                    message = "Content-Type は application/json か multipart/form-data にすること",
                    status = HttpStatusCode.UnsupportedMediaType,
                ),
            )
        }
    }
}

private suspend fun respond(
    call: ApplicationCall,
    engine: GraphQlEngine,
    received: ReceivedGraphQlRequest,
) {
    when (received) {
        is ReceivedGraphQlRequest.Rejected -> {
            call.respondText(received.message, status = received.status)
        }

        ReceivedGraphQlRequest.Unreadable -> {
            call.respondJson(
                GraphQlBadRequest.serializer(),
                GraphQlBadRequest("GraphQL のリクエストとして読めない"),
                status = HttpStatusCode.BadRequest,
            )
        }

        is ReceivedGraphQlRequest.Accepted -> {
            // graphql-java の execute は blocking で、CIO はルート内の blocking を別スレッドに移さない
            val result = withOpenTelemetryContext { engine.execute(received.request, call, received.uploads) }
            call.respondJson(JsonObject.serializer(), result)
        }
    }
}

/**
 * 受け取ったファイルは応答を返すまで使うので、返した後にまとめて消す
 */
private suspend fun respondMultipart(
    call: ApplicationCall,
    engine: GraphQlEngine,
) {
    val uploadDirectory = withContext(Dispatchers.IO) { Files.createTempDirectory(UPLOAD_DIRECTORY_PREFIX) }
    try {
        respond(call, engine, receiveMultipart(call, uploadDirectory))
    } finally {
        withContext(NonCancellable + Dispatchers.IO) { uploadDirectory.toFile().deleteRecursively() }
    }
}

private suspend fun receiveJson(call: ApplicationCall): ReceivedGraphQlRequest {
    if (call.declaredLengthExceeds(MAX_BODY_BYTES.toLong())) return payloadTooLarge()

    // chunked は上の判定をすり抜ける
    val bodyBytes = call.receiveChannel().readBuffer(MAX_BODY_BYTES + 1L).readByteArray()
    if (bodyBytes.size > MAX_BODY_BYTES) return payloadTooLarge()

    val request = decodeRequest(bodyBytes.decodeToString()) ?: return ReceivedGraphQlRequest.Unreadable
    return ReceivedGraphQlRequest.Accepted(request = request, uploads = mapOf())
}

/**
 * GraphQL multipart request の形を受ける。ファイルは変数の直下にだけ置ける
 */
private suspend fun receiveMultipart(
    call: ApplicationCall,
    uploadDirectory: Path,
): ReceivedGraphQlRequest {
    if (call.request.header(REQUIRE_PREFLIGHT_HEADER) == null) {
        return ReceivedGraphQlRequest.Rejected(message = "$REQUIRE_PREFLIGHT_HEADER ヘッダーを付けること", status = HttpStatusCode.BadRequest)
    }
    if (call.declaredLengthExceeds(MAX_MULTIPART_BODY_BYTES.toLong())) return payloadTooLarge()

    val fields = mutableMapOf<String, String>()
    val files = mutableMapOf<String, GraphQlUpload>()
    var receivedBytes = 0L
    var tooLarge = false
    // Ktor は部分ごとの上限を超えると、解析しているコルーチンごと呼び出しを 500 で落とす。
    // ここでは 413 を返せるよう、Ktor の上限は本文全体の大きさにし、受け取った量は自分で数える。
    // chunked では Content-Length の判定をすり抜けるので、部分ごとではなく合計で数える
    call.receiveMultipart(formFieldLimit = MAX_MULTIPART_BODY_BYTES.toLong()).forEachPart { part ->
        try {
            val name = part.name
            val remainingBytes = MAX_MULTIPART_BODY_BYTES - receivedBytes
            when {
                name == null || tooLarge -> Unit

                part is PartData.FormItem -> {
                    receivedBytes += part.value.encodeToByteArray().size
                    if (receivedBytes > MAX_MULTIPART_BODY_BYTES) tooLarge = true else fields[name] = part.value
                }

                part is PartData.FileItem -> {
                    val readLimit = minOf(NotePostService.MAX_IMAGE_BYTES.toLong(), remainingBytes) + 1
                    val file = uploadDirectory.resolve(files.size.toString())
                    val size = part.provider().writeAtMost(file = file, maxBytes = readLimit)
                    receivedBytes += size
                    if (size > NotePostService.MAX_IMAGE_BYTES || receivedBytes > MAX_MULTIPART_BODY_BYTES) {
                        tooLarge = true
                    } else {
                        files[name] = GraphQlUpload(file = file, size = size)
                    }
                }

                else -> Unit
            }
        } finally {
            part.dispose()
        }
    }
    if (tooLarge) return payloadTooLarge()

    val request = fields["operations"]?.let(::decodeRequest) ?: return ReceivedGraphQlRequest.Unreadable
    val map =
        fields["map"]?.let { runCatching { ServerJson.value.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?: return ReceivedGraphQlRequest.Unreadable
    val uploads =
        map.entries.associate { (fileName, paths) ->
            val path = ((paths as? JsonArray)?.singleOrNull() as? JsonPrimitive)?.content ?: return ReceivedGraphQlRequest.Unreadable
            val variableName = path.removePrefix("variables.").takeIf { path.startsWith("variables.") && !it.contains('.') }
                ?: return ReceivedGraphQlRequest.Unreadable
            val upload = files[fileName] ?: return ReceivedGraphQlRequest.Unreadable
            variableName to upload
        }
    return ReceivedGraphQlRequest.Accepted(request = request, uploads = uploads)
}

/**
 * @return 書いたバイト数。[maxBytes] で打ち切る
 */
private suspend fun ByteReadChannel.writeAtMost(
    file: Path,
    maxBytes: Long,
): Long =
    withContext(Dispatchers.IO) {
        Files.newOutputStream(file).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var written = 0L
            while (written < maxBytes) {
                val read = readAvailable(buffer, 0, minOf(buffer.size.toLong(), maxBytes - written).toInt())
                if (read == -1) break
                output.write(buffer, 0, read)
                written += read
            }
            written
        }
    }

private fun ApplicationCall.declaredLengthExceeds(maxBytes: Long): Boolean {
    val declaredLength = request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: return false
    return declaredLength > maxBytes
}

private fun decodeRequest(body: String): GraphQlRequest? =
    runCatching { ServerJson.value.decodeFromString(GraphQlRequest.serializer(), body) }.getOrNull()

private fun payloadTooLarge(): ReceivedGraphQlRequest = ReceivedGraphQlRequest.Rejected(message = "ボディが大きすぎる", status = HttpStatusCode.PayloadTooLarge)

private sealed interface ReceivedGraphQlRequest {
    class Accepted(
        val request: GraphQlRequest,
        val uploads: Map<String, GraphQlUpload>,
    ) : ReceivedGraphQlRequest

    class Rejected(
        val message: String,
        val status: HttpStatusCode,
    ) : ReceivedGraphQlRequest

    data object Unreadable : ReceivedGraphQlRequest
}
