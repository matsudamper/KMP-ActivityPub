package net.matsudamper.kmp.activitypub.logic

import java.net.IDN
import java.net.URI

/**
 * サーバーを表すホスト。小文字の ASCII で、既定でないポートがあれば `:ポート` まで含める
 */
object ServerHost {
    private val HOST_PATTERN = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*(:[0-9]{1,5})?")

    /**
     * `example.com`、`https://example.com/@user`、`@user@example.com` のどれからでもホストを取り出す。取り出せなければ null
     */
    fun parse(input: String): String? {
        val trimmed = input.trim()
        val authority = when {
            trimmed.contains("://") -> runCatching { URI(trimmed).rawAuthority }.getOrNull()?.substringAfterLast('@')
            else -> trimmed.substringAfterLast('@').substringBefore('/')
        } ?: return null
        val port = authority.substringAfter(':', missingDelimiterValue = "")
        val asciiHost = runCatching { IDN.toASCII(authority.substringBefore(':')) }.getOrNull() ?: return null
        val host = if (port.isEmpty()) asciiHost else "$asciiHost:$port"
        return host.lowercase().takeIf { it.matches(HOST_PATTERN) }
    }

    /**
     * アクターの URI のホスト。読めなければ null
     */
    fun ofUri(uri: String): String? {
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        val host = parsed.host?.lowercase() ?: return null
        return if (parsed.port == -1) host else "$host:${parsed.port}"
    }
}
