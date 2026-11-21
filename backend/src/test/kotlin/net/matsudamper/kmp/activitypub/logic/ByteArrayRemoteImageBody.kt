package net.matsudamper.kmp.activitypub.logic

/**
 * 相手の画像の代わりに、決まったバイト列を少しずつ返す
 */
class ByteArrayRemoteImageBody(
    private val bytes: ByteArray,
    override val contentLength: Long? = bytes.size.toLong(),
) : RemoteImageBody {
    private var position = 0

    override suspend fun read(buffer: ByteArray): Int {
        if (position >= bytes.size) return -1
        val length = minOf(buffer.size, bytes.size - position)
        bytes.copyInto(buffer, startIndex = position, endIndex = position + length)
        position += length
        return length
    }

    override fun close() = Unit
}
