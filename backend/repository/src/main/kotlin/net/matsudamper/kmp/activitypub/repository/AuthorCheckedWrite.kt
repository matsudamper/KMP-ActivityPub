package net.matsudamper.kmp.activitypub.repository

/**
 * 保存済みの投稿者を見込みと見比べてから書き込んだ結果
 */
sealed interface AuthorCheckedWrite<out T> {
    data class Written<T>(val value: T) : AuthorCheckedWrite<T>

    /**
     * 保存済みの投稿者が見込みと食い違ったので書かなかった
     */
    data object AuthorChanged : AuthorCheckedWrite<Nothing>
}
