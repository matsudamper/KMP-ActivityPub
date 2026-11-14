package net.matsudamper.kmp.activitypub.frontend.screen.reaction

/**
 * @param searchTerms 日本語と英語の名前とキーワード。[EmojiSearchText.normalize] 済み
 */
internal class UnicodeEmoji(
    val emoji: String,
    val category: UnicodeEmojiCategory,
    val searchTerms: List<String>,
)

/**
 * ピッカーで見出しにする分類。並びは Unicode の分類の順
 */
internal enum class UnicodeEmojiCategory(val label: String) {
    SmileysAndEmotion("顔と感情"),
    PeopleAndBody("人と体"),
    AnimalsAndNature("動物と自然"),
    FoodAndDrink("食べ物と飲み物"),
    TravelAndPlaces("旅行と場所"),
    Activities("活動"),
    Objects("物"),
    Symbols("記号"),
    Flags("旗"),
}

internal object UnicodeEmojis {
    /**
     * 千を超える絵文字を読み解くので、ピッカーを初めて開くまで読まない
     */
    val all: List<UnicodeEmoji> by lazy {
        UNICODE_EMOJI_DATA.flatMap { chunk -> chunk.lineSequence() }
            .filter { it.isNotBlank() }
            .map { line ->
                val (emoji, categoryIndex, terms) = line.split('\t', limit = 3)
                UnicodeEmoji(
                    emoji = emoji,
                    category = UnicodeEmojiCategory.entries[categoryIndex.toInt()],
                    searchTerms = terms.split('|').map(EmojiSearchText::normalize),
                )
            }
            .toList()
    }

    /**
     * @param query 空なら全部
     */
    fun search(query: String): List<UnicodeEmoji> {
        val normalized = EmojiSearchText.normalize(query.trim())
        if (normalized.isEmpty()) return all
        return all.filter { emoji -> emoji.searchTerms.any { it.contains(normalized) } }
    }
}

internal object EmojiSearchText {
    private const val KATAKANA_TO_HIRAGANA_OFFSET = 'ア' - 'あ'

    /**
     * 大文字と小文字、カタカナとひらがなの違いで見つからなくならないよう揃える
     */
    fun normalize(text: String): String = buildString(text.length) {
        text.lowercase().forEach { char ->
            append(if (char in 'ァ'..'ヶ') char - KATAKANA_TO_HIRAGANA_OFFSET else char)
        }
    }
}
