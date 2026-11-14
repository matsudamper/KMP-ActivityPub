package net.matsudamper.kmp.activitypub.frontend.core

/**
 * `/@{username}` のように `{名前}` の部分を 1 階層分の可変部分として扱う。
 * 値に `/` などパスを区切る文字が入っても 1 階層に収まるよう、[pathOf] で % エンコードし、[match] で戻す
 */
class PathPattern(
    private val pattern: String,
) {
    private val parameterNames: List<String> = PARAMETER.findAll(pattern).map { it.groupValues[1] }.toList()

    private val regex: Regex =
        PARAMETER
            .split(pattern)
            .joinToString(separator = "([^/]+)", prefix = "^", postfix = "$") { Regex.escape(it) }
            .toRegex()

    /**
     * 合わなければ null。合えば可変部分の名前と値
     */
    fun match(path: String): Map<String, String>? {
        val result = regex.matchEntire(path) ?: return null
        return parameterNames.zip(result.groupValues.drop(1).map(::decodeSegment)).toMap()
    }

    fun pathOf(parameters: Map<String, String>): String {
        return PARAMETER.replace(pattern) { encodeSegment(checkNotNull(parameters[it.groupValues[1]])) }
    }

    private companion object {
        val PARAMETER = Regex("""\{([^}/]+)\}""")

        /**
         * パスの区切りとして読まれる文字だけを % エンコードする。`@` や `.` はユーザー名の URL を読みやすく保つためそのままにする
         */
        val ESCAPED_CHARACTERS = setOf('%', '/', '?', '#')

        fun encodeSegment(value: String): String = buildString {
            value.forEach { char ->
                if (char in ESCAPED_CHARACTERS) {
                    append('%')
                    append(char.code.toString(16).uppercase().padStart(2, '0'))
                } else {
                    append(char)
                }
            }
        }

        /**
         * ブラウザが日本語などを % エンコードしたパスも戻せるよう、エンコードした文字に限らず UTF-8 として戻す
         */
        fun decodeSegment(segment: String): String {
            if ('%' !in segment) return segment
            val bytes = buildList {
                var index = 0
                while (index < segment.length) {
                    val escapedByte = if (segment[index] == '%' && index + 2 < segment.length) {
                        segment.substring(index + 1, index + 3).toIntOrNull(16)
                    } else {
                        null
                    }
                    if (escapedByte != null) {
                        add(escapedByte.toByte())
                        index += 3
                    } else {
                        addAll(segment[index].toString().encodeToByteArray().toList())
                        index += 1
                    }
                }
            }
            return bytes.toByteArray().decodeToString()
        }
    }
}
