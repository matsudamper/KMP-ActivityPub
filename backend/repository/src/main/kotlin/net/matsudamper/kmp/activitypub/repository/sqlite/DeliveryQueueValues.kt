package net.matsudamper.kmp.activitypub.repository.sqlite

/**
 * delivery_queue の kind と state に入れる綴り。DB の CHECK では縛らないので、取りうる値はここだけが持つ
 */
internal object DeliveryQueueValues {
    const val KIND_ACCEPT_FOLLOW = "accept_follow"
    const val KIND_FOLLOW = "follow"
    const val KIND_UNDO_FOLLOW = "undo_follow"
    const val KIND_CREATE_NOTE = "create_note"
    const val KIND_UPDATE_ACTOR = "update_actor"
    const val KIND_DELETE_NOTE = "delete_note"
    const val KIND_LIKE = "like"
    const val KIND_UNDO_LIKE = "undo_like"

    /**
     * 送っている途中にいいねを外した Like。送れなかったら送り直さずに消す
     */
    const val KIND_CANCELLED_LIKE = "cancelled_like"

    const val KIND_ANNOUNCE = "announce"
    const val KIND_UNDO_ANNOUNCE = "undo_announce"

    /**
     * 送っている途中にブーストを外した Announce。送れなかったら送り直さずに消す
     */
    const val KIND_CANCELLED_ANNOUNCE = "cancelled_announce"

    /**
     * 送れなかったら送り直さずに消す種別
     */
    val CANCELLED_KINDS = listOf(KIND_CANCELLED_LIKE, KIND_CANCELLED_ANNOUNCE)

    const val STATE_PENDING = "pending"
    const val STATE_DELIVERING = "delivering"
    const val STATE_FAILED = "failed"
}
