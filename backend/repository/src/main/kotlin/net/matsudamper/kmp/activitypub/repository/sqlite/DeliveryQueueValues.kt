package net.matsudamper.kmp.activitypub.repository.sqlite

/**
 * delivery_queue の kind と state に入れる綴り。schema.sql の CHECK と揃える
 */
internal object DeliveryQueueValues {
    const val KIND_ACCEPT_FOLLOW = "accept_follow"
    const val KIND_CREATE_NOTE = "create_note"

    const val STATE_PENDING = "pending"
    const val STATE_DELIVERING = "delivering"
    const val STATE_FAILED = "failed"
}
