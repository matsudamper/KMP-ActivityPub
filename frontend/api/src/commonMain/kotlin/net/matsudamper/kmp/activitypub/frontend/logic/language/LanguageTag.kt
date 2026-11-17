package net.matsudamper.kmp.activitypub.frontend.logic.language

/**
 * 言語（ISO 639-1）に、文字体系（ISO 15924）と地域（ISO 3166-1 / UN M.49）を付けられる BCP 47 のタグ。
 * [parse] を通った値だけを持つ
 */
@ConsistentCopyVisibility
data class LanguageTag private constructor(
    val value: String,
) {
    companion object {
        private val Pattern = Regex("([A-Za-z]{2})(?:-([A-Za-z]{4}))?(?:-([A-Za-z]{2}|[0-9]{3}))?")

        /**
         * @return 有効な言語でなければ null。有効なら `zh-Hant-TW` のように大文字小文字をそろえる
         */
        fun parse(text: String): LanguageTag? {
            val match = Pattern.matchEntire(text.trim())
            return if (match == null) {
                null
            } else {
                val (language, script, region) = match.destructured
                if (language.lowercase() in Iso639Part1Codes) {
                    LanguageTag(
                        listOf(
                            language.lowercase(),
                            script.lowercase().replaceFirstChar { it.uppercase() },
                            region.uppercase(),
                        ).filter { it.isNotEmpty() }.joinToString("-"),
                    )
                } else {
                    null
                }
            }
        }
    }
}

private val Iso639Part1Codes: Set<String> =
    (
        "aa ab ae af ak am an ar as av ay az ba be bg bh bi bm bn bo br bs ca ce ch co cr cs cu cv cy " +
            "da de dv dz ee el en eo es et eu fa ff fi fj fo fr fy ga gd gl gn gu gv ha he hi ho hr ht hu hy hz " +
            "ia id ie ig ii ik io is it iu ja jv ka kg ki kj kk kl km kn ko kr ks ku kv kw ky " +
            "la lb lg li ln lo lt lu lv mg mh mi mk ml mn mr ms mt my na nb nd ne ng nl nn no nr nv ny " +
            "oc oj om or os pa pi pl ps pt qu rm rn ro ru rw sa sc sd se sg si sk sl sm sn so sq sr ss st su sv sw " +
            "ta te tg th ti tk tl tn to tr ts tt tw ty ug uk ur uz ve vi vo wa wo xh yi yo za zh zu"
        ).split(" ").toSet()
