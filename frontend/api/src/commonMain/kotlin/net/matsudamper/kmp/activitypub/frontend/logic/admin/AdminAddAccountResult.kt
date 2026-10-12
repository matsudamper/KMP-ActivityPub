package net.matsudamper.kmp.activitypub.frontend.logic.admin
sealed interface AdminAddAccountResult {
    data class Success(
        val username: String,
    ) : AdminAddAccountResult

    /**
     * 追加できなかった理由。当てはまらないものは null か空で来る。
     */
    data class Rejected(
        val unusableCharacters: List<String>,
        val maxLength: Int?,
        val minLength: Int?,
        val isDuplicated: Boolean,
        val passwordMinLength: Int?,
    ) : AdminAddAccountResult

    data class Failure(
        val message: String,
    ) : AdminAddAccountResult
}
