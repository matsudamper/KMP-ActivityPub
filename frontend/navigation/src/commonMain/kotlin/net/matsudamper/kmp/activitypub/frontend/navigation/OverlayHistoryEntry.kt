package net.matsudamper.kmp.activitypub.frontend.navigation

/**
 * 画面の積み上げに載らない重ね表示（動画など）が、ブラウザの戻るで閉じられるように積む履歴。
 * 閉じたあとも「進む」で着けてしまうので、着いたら飛ばせるよう通常の履歴と見分ける
 */
object OverlayHistoryEntry {
    private const val STATE_PREFIX = "overlay\n"

    /**
     * @param baseState 重ねる前の画面の履歴の state。見分けたあと、その画面の履歴として読めるよう中に残す
     */
    fun stateOf(baseState: String): String = STATE_PREFIX + baseState

    /**
     * 重ね表示の履歴なら、重ねる前の画面の state を返す
     */
    fun baseStateOf(state: String): String? = state.removePrefix(STATE_PREFIX).takeIf { state.startsWith(STATE_PREFIX) }
}
