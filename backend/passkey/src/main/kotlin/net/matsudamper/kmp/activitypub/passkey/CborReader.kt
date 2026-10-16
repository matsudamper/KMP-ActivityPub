package net.matsudamper.kmp.activitypub.passkey

/**
 * WebAuthn の attestationObject と COSE 鍵を読むための CBOR。
 * 認証器は CTAP2 の正準形で出すので、長さ不定・浮動小数点・タグは読めないものとして弾く。
 *
 * 値は Long / ByteArray / String / List / Map / Boolean / null で返す
 */
internal class CborReader(
    private val bytes: ByteArray,
    startIndex: Int,
) {
    var position: Int = startIndex
        private set

    fun read(): Any? = read(depth = 0)

    private fun read(depth: Int): Any? {
        if (depth > MAX_DEPTH) throw PasskeyVerificationException("CBOR の入れ子が深すぎる")

        val initial = nextByte()
        val majorType = initial ushr 5
        val additional = initial and 0x1f

        return when (majorType) {
            MAJOR_UNSIGNED -> {
                readArgument(additional)
            }

            MAJOR_NEGATIVE -> {
                -1 - readArgument(additional)
            }

            MAJOR_BYTES -> {
                readBytes(readLength(additional))
            }

            MAJOR_TEXT -> {
                readBytes(readLength(additional)).decodeToString(throwOnInvalidSequence = true)
            }

            MAJOR_ARRAY -> {
                List(readLength(additional)) { read(depth + 1) }
            }

            MAJOR_MAP -> {
                val size = readLength(additional)
                buildMap {
                    repeat(size) {
                        val key = read(depth + 1)
                        if (key !is Long && key !is String) throw PasskeyVerificationException("CBOR の map のキーが整数でも文字列でもない")
                        if (containsKey(key)) throw PasskeyVerificationException("CBOR の map のキーが重複している")
                        put(key, read(depth + 1))
                    }
                }
            }

            MAJOR_SIMPLE -> {
                when (additional) {
                    SIMPLE_FALSE -> false
                    SIMPLE_TRUE -> true
                    SIMPLE_NULL -> null
                    else -> throw PasskeyVerificationException("扱わない CBOR の simple 値: $additional")
                }
            }

            else -> {
                throw PasskeyVerificationException("扱わない CBOR の型: $majorType")
            }
        }
    }

    private fun readLength(additional: Int): Int {
        val length = readArgument(additional)
        if (length > bytes.size - position) throw PasskeyVerificationException("CBOR の長さが残りのバイト数を超えている")
        return length.toInt()
    }

    private fun readArgument(additional: Int): Long {
        val value =
            when (additional) {
                in 0..23 -> additional.toLong()
                24 -> readUnsigned(1)
                25 -> readUnsigned(2)
                26 -> readUnsigned(4)
                27 -> readUnsigned(8)
                else -> throw PasskeyVerificationException("長さ不定などの CBOR は扱わない")
            }
        // 8 バイトの値は Long に収まらないと負になる
        if (value < 0) throw PasskeyVerificationException("CBOR の整数が大きすぎる")
        return value
    }

    private fun readUnsigned(size: Int): Long {
        var value = 0L
        repeat(size) {
            value = (value shl 8) or nextByte().toLong()
        }
        return value
    }

    private fun readBytes(length: Int): ByteArray {
        val result = bytes.copyOfRange(position, position + length)
        position += length
        return result
    }

    private fun nextByte(): Int {
        if (position >= bytes.size) throw PasskeyVerificationException("CBOR が途中で終わっている")
        return bytes[position++].toInt() and 0xff
    }

    private companion object {
        const val MAJOR_UNSIGNED = 0
        const val MAJOR_NEGATIVE = 1
        const val MAJOR_BYTES = 2
        const val MAJOR_TEXT = 3
        const val MAJOR_ARRAY = 4
        const val MAJOR_MAP = 5
        const val MAJOR_SIMPLE = 7

        const val SIMPLE_FALSE = 20
        const val SIMPLE_TRUE = 21
        const val SIMPLE_NULL = 22

        const val MAX_DEPTH = 16
    }
}
