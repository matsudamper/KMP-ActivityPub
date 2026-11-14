package net.matsudamper.kmp.activitypub.logic

import java.net.IDN
import java.net.URI

/**
 * サーバーを表すホスト。小文字の ASCII。
 * 同じサーバーでも `:443` の有無や、プロフィールに出すホストとアクターの URI でポートの書き方が揃わないので、ポートは含めない
 */
object ServerHost {
    private val HOST_PATTERN = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*")

    /**
     * `example.com`、`https://example.com/@user`、`@user@example.com` のどれからでもホストを取り出す。取り出せなければ null
     */
    fun parse(input: String): String? {
        val trimmed = input.trim()
        val authority = when {
            trimmed.contains("://") -> runCatching { URI(trimmed).rawAuthority }.getOrNull()?.substringAfterLast('@')
            else -> trimmed.substringAfterLast('@').substringBefore('/')
        } ?: return null
        val asciiHost = runCatching { IDN.toASCII(authority.substringBefore(':')) }.getOrNull() ?: return null
        return asciiHost.lowercase().takeIf { it.matches(HOST_PATTERN) }
    }

    /**
     * アクターの URI のホスト。読めなければ null
     */
    fun ofUri(uri: String): String? = runCatching { URI(uri) }.getOrNull()?.host?.lowercase()
}
