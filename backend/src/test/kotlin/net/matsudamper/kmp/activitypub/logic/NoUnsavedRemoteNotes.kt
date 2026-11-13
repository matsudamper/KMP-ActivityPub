package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 保存していない投稿を取りに行かないテスト用
 */
object NoUnsavedRemoteNotes : UnsavedRemoteNotes {
    override fun storedMember(viewer: AccountId, objectUri: String): String? = null

    override suspend fun import(viewer: AccountId, objectUri: String): String? = null
}
