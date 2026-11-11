package net.matsudamper.kmp.activitypub.repository.sqlite

/**
 * タイムラインの member の綴りを読む。`l` は NOTES、`r` は REMOTE_NOTES、`b` は REMOTE_BOOSTS、`a` は ACCOUNT_BOOSTS の id を指す
 */
internal object TimelineMembers {
    fun localNoteIdOf(member: String): Long? =
        if (member.startsWith("l")) member.drop(1).toLongOrNull() else null

    fun remoteNoteIdOf(member: String): Long? =
        if (member.startsWith("r")) member.drop(1).toLongOrNull() else null

    fun boostIdOf(member: String): Long? =
        if (member.startsWith("b")) member.drop(1).toLongOrNull() else null

    /**
     * このサーバーのアカウントが付けたブースト。ブーストしたアカウントのタイムラインにだけ載る
     */
    fun accountBoostIdOf(member: String): Long? =
        if (member.startsWith("a")) member.drop(1).toLongOrNull() else null

    fun accountBoostMember(accountBoostId: Long): String = "a$accountBoostId"
}
