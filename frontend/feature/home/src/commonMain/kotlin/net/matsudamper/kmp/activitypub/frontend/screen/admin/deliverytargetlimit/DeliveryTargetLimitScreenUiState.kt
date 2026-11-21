package net.matsudamper.kmp.activitypub.frontend.screen.admin.deliverytargetlimit

import androidx.compose.runtime.Immutable

data class DeliveryTargetLimitScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * 読めなかった理由は Snackbar で出すので、ここでは読み直す操作だけを出す
         */
        data object LoadFailed : Content

        /**
         * @param hosts 配る先の数の多い順
         */
        data class Loaded(
            val currentLimitText: String,
            val limitInput: String,
            val limitInputEnabled: Boolean,
            val saveButtonEnabled: Boolean,
            val hosts: List<Host>,
            val emptyMessageVisible: Boolean,
        ) : Content
    }

    /**
     * @param countText 配る先の数と上限
     * @param rowHighlighted このホストから配る先が増えるフォローは断られるので、目立たせる
     * @param warningText 出さなければ null
     */
    data class Host(
        val host: String,
        val countText: String,
        val rowHighlighted: Boolean,
        val warningText: String?,
    )

    @Immutable
    interface Listener {
        fun onClickBack()

        fun onClickRetry()

        fun onChangeLimitInput(value: String)

        fun onClickSave()
    }
}
