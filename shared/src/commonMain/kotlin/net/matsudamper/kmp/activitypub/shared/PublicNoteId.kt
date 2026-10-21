package net.matsudamper.kmp.activitypub.shared

/**
 * 投稿の URL に出す識別子。連番の id を出すと投稿の総数が外から分かるので別に持つ
 */
data class PublicNoteId(
    val value: String,
)
