package net.matsudamper.kmp.activitypub.frontend.screen.admin

/**
 * サーバーの配信キューの種別の綴りを、画面に出す名前にする。知らない綴りはそのまま出す
 */
internal fun createFailedDeliveryKindText(kind: String): String {
    return when (kind) {
        "accept_follow" -> "フォローの承認"
        "accept_quote" -> "引用の承認"
        "follow" -> "フォロー"
        "undo_follow" -> "フォローの解除"
        "reject_follow" -> "フォローの拒否"
        "block" -> "ブロック"
        "undo_block" -> "ブロックの解除"
        "cancelled_block" -> "取り消したブロック"
        "create_note" -> "投稿"
        "update_actor" -> "プロフィールの更新"
        "delete_note" -> "投稿の削除"
        "like" -> "いいね"
        "undo_like" -> "いいねの取り消し"
        "cancelled_like" -> "取り消したいいね"
        "announce" -> "ブースト"
        "undo_announce" -> "ブーストの取り消し"
        "cancelled_announce" -> "取り消したブースト"
        else -> kind
    }
}
