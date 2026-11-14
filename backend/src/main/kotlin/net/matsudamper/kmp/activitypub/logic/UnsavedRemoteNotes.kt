package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 保存していない他のサーバーの投稿を、いいねやブーストの投稿の id で指す。押したときに投稿を取ってきて保存する。
 * タイムラインの member（`l` / `r` / `b` / `a` と数字）とは先頭の文字で分ける
 */
object UnsavedRemoteNoteId {
    private const val PREFIX = "u"

    fun of(objectUri: String): String = PREFIX + objectUri

    /**
     * @return [postId] が保存していない投稿を指していなければ null
     */
    fun objectUriOf(postId: String): String? {
        if (!postId.startsWith(PREFIX)) return null
        return postId.removePrefix(PREFIX).takeIf { it.startsWith("https://") }
    }
}

/**
 * 保存していない他のサーバーの投稿を、いいねやブーストを付けるときに保存する
 */
interface UnsavedRemoteNotes {
    /**
     * @return 保存済みで [viewer] が読めるなら、いいねやブーストに渡すタイムラインの member。無ければ null
     */
    fun storedMember(viewer: AccountId, objectUri: String): String?

    /**
     * 投稿を取ってきて保存する
     *
     * @return 保存した投稿の、いいねやブーストに渡すタイムラインの member。取れないか、保存できない投稿なら null
     */
    suspend fun import(viewer: AccountId, objectUri: String): String?

    /**
     * 投稿と投稿者を取ってくる。どちらも保存しない
     *
     * @return 取れないか、公開と公開タイムラインに載せない公開のどちらでもない投稿なら null
     */
    suspend fun fetch(objectUri: String): FetchedRemoteNote?
}

/**
 * @param author 投稿者。保存済みならその記録
 */
data class FetchedRemoteNote(
    val note: RemoteNoteInput,
    val author: RemoteUser,
)

/**
 * いいねやブーストに渡された投稿の id を、タイムラインの member にする
 *
 * @param importsMissing 保存していなければ取ってきて保存するか。外すときは、付けていない投稿を保存しないよう false にする
 * @return [postId] がもともとタイムラインの member ならそのまま。保存していない投稿で、保存しないか保存できなければ null
 */
suspend fun UnsavedRemoteNotes.memberOf(viewer: AccountId, postId: String, importsMissing: Boolean): String? {
    val objectUri = UnsavedRemoteNoteId.objectUriOf(postId) ?: return postId
    return storedMember(viewer, objectUri) ?: if (importsMissing) import(viewer, objectUri) else null
}
