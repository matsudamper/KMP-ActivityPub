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

    const val STATE_PENDING = "pending"
    const val STATE_DELIVERING = "delivering"
    const val STATE_FAILED = "failed"
}
