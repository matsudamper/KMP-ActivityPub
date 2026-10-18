package net.matsudamper.kmp.activitypub.repository.sqlite

import java.net.URI
import java.util.Locale

internal object InboxHost {
    /**
     * ホスト名は大文字小文字を区別しないので、揃えてから鍵にする。
     *
     * 読めない URL は URL 全体を鍵にする。同じ壊れた宛先同士だけが同じ鍵になる
     */
    fun of(inbox: String): String = runCatching { URI(inbox).host?.lowercase(Locale.ROOT) }.getOrNull() ?: inbox
}
