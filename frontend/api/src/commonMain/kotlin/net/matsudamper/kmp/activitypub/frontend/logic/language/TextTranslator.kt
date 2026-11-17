package net.matsudamper.kmp.activitypub.frontend.logic.language

/**
 * 端末の中で動く翻訳。本文をこのサーバーには送らない
 */
interface TextTranslator {
    /**
     * この端末で翻訳を使えるか。使えなければ翻訳のボタンを出さない
     */
    val supported: Boolean

    /**
     * @param sourceLanguageTag 元の文の BCP 47 の言語タグ
     * @return 翻訳できなかったら null。言語の組み合わせに対応していないときも null
     */
    suspend fun translate(text: String, sourceLanguageTag: String, targetLanguage: LanguageTag): String?
}
