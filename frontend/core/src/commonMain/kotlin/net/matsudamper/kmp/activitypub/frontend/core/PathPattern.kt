package net.matsudamper.kmp.activitypub.frontend.core

/**
 * `/@{username}` のように `{名前}` の部分を 1 階層分の可変部分として扱う
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
        return parameterNames.zip(result.groupValues.drop(1)).toMap()
    }

    fun pathOf(parameters: Map<String, String>): String {
        return PARAMETER.replace(pattern) { checkNotNull(parameters[it.groupValues[1]]) }
    }

    private companion object {
        val PARAMETER = Regex("""\{([^}/]+)\}""")
    }
}
