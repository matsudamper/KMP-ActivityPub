package net.matsudamper.kmp.activitypub.android.language

import kotlinx.coroutines.tasks.await
import com.google.mlkit.common.MlKitException
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageTag
import net.matsudamper.kmp.activitypub.frontend.logic.language.TextTranslator
import org.koin.core.annotation.Singleton

/**
 * ML Kit の端末内翻訳。初めて使う言語は、翻訳のモデルをダウンロードしてから訳す
 */
@Singleton
internal class MlKitTextTranslator : TextTranslator {
    override val supported: Boolean = true

    override suspend fun translate(text: String, sourceLanguageTag: String, targetLanguage: LanguageTag): String? {
        val source = TranslateLanguage.fromLanguageTag(primaryLanguageOf(sourceLanguageTag)) ?: return null
        val target = TranslateLanguage.fromLanguageTag(primaryLanguageOf(targetLanguage.value)) ?: return null
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build(),
        )
        return try {
            translator.downloadModelIfNeeded().await()
            translator.translate(text).await()
        } catch (_: MlKitException) {
            null
        } finally {
            translator.close()
        }
    }

    // ML Kit は地域や文字体系の付いたタグを受け付けない
    private fun primaryLanguageOf(languageTag: String): String = languageTag.substringBefore('-')
}
