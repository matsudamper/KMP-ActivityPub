package net.matsudamper.kmp.activitypub.frontend

import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.Promise
import kotlinx.coroutines.await
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageTag
import net.matsudamper.kmp.activitypub.frontend.logic.language.TextTranslator

/**
 * ブラウザの Translator API で翻訳する。対応しているブラウザは限られる
 */
internal class WebTextTranslator : TextTranslator {
    override val supported: Boolean = translatorApiExists()

    override suspend fun translate(text: String, sourceLanguageTag: String, targetLanguage: LanguageTag): String? =
        try {
            translateInBrowser(text = text, sourceLanguage = sourceLanguageTag, targetLanguage = targetLanguage.value).await<JsString?>()?.toString()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // モデルを取れない、利用者の操作が要る、などで Promise が失敗する
            null
        }
}

private fun translatorApiExists(): Boolean = js("typeof Translator !== 'undefined'")

private fun translateInBrowser(text: String, sourceLanguage: String, targetLanguage: String): Promise<JsString?> = js(
    """
    (async () => {
        const languages = { sourceLanguage: sourceLanguage, targetLanguage: targetLanguage };
        if (await Translator.availability(languages) === 'unavailable') return null;
        const translator = await Translator.create(languages);
        try {
            return await translator.translate(text);
        } finally {
            translator.destroy();
        }
    })()
    """,
)
