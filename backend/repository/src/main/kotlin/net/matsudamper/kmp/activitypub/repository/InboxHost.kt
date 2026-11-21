package net.matsudamper.kmp.activitypub.repository

import java.net.URI

/**
 * 配信の宛先 inbox のホスト。同じホスト宛ての配信をまとめて扱うときの鍵
 */
object InboxHost {
    /**
     * URL として読めない inbox は、文字列そのものを 1 つのホストとして扱う
     */
    fun of(inbox: String): String =
        runCatching { URI(inbox).host }.getOrNull()?.lowercase() ?: inbox
}
