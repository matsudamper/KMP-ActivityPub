package net.matsudamper.kmp.activitypub.frontend.screen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageTag
import net.matsudamper.kmp.activitypub.frontend.logic.language.TextTranslator

/**
 * 投稿の翻訳の進み具合を、画面ごとに持つ。同じ言語の同じ本文は同じ訳になるので、本文と言語で引く
 *
 * @param onFailed 翻訳できなかったとき
 */
internal class PostTranslations(
    private val translator: TextTranslator,
    languageSettingStore: LanguageSettingStore,
    private val coroutineScope: CoroutineScope,
    private val onFailed: () -> Unit,
) {
    private val mutableStateFlow = MutableStateFlow(
        PostTranslationState(viewerLanguage = null, translatorSupported = translator.supported, progressBySource = mapOf()),
    )
    val stateFlow: StateFlow<PostTranslationState> = mutableStateFlow.asStateFlow()

    init {
        coroutineScope.launch {
            languageSettingStore.languageSettingFlow.collect { setting ->
                mutableStateFlow.update { state ->
                    if (state.viewerLanguage == setting.languageTag) {
                        state
                    } else {
                        // 前の言語への訳を残すと、ボタンが消えて原文に戻せなくなることがある
                        state.copy(viewerLanguage = setting.languageTag, progressBySource = mapOf())
                    }
                }
            }
        }
    }

    /**
     * 訳していなければ訳し、訳を出していれば原文に戻す
     */
    fun toggle(source: PostTranslationSource) {
        val state = mutableStateFlow.value
        val targetLanguage = state.viewerLanguage ?: return
        when (state.progressBySource[source]) {
            PostTranslationProgress.Translating -> Unit

            is PostTranslationProgress.Translated -> updateProgress { it - source }

            null -> {
                updateProgress { it + (source to PostTranslationProgress.Translating) }
                coroutineScope.launch {
                    val translated = translator.translate(text = source.text, sourceLanguageTag = source.languageTag, targetLanguage = targetLanguage)
                    // 訳している間に設定の言語が変わったら、前の言語への訳なので捨てる
                    if (mutableStateFlow.value.viewerLanguage != targetLanguage) return@launch
                    if (translated == null) {
                        updateProgress { it - source }
                        onFailed()
                    } else {
                        updateProgress { it + (source to PostTranslationProgress.Translated(translated)) }
                    }
                }
            }
        }
    }

    private fun updateProgress(block: (Map<PostTranslationSource, PostTranslationProgress>) -> Map<PostTranslationSource, PostTranslationProgress>) {
        mutableStateFlow.update { it.copy(progressBySource = block(it.progressBySource)) }
    }
}

/**
 * @param viewerLanguage 設定した言語。まだ読めていないか、決まっていなければ null
 * @param translatorSupported この端末で翻訳を使えるか
 * @param progressBySource 訳している途中か、訳し終えた本文。原文のまま出している本文は入らない
 */
internal data class PostTranslationState(
    val viewerLanguage: LanguageTag?,
    val translatorSupported: Boolean,
    val progressBySource: Map<PostTranslationSource, PostTranslationProgress>,
)

/**
 * @param languageTag [text] の BCP 47 の言語タグ。同じ綴りでも言語が違えば訳が違う
 */
internal data class PostTranslationSource(val text: String, val languageTag: String)

internal sealed interface PostTranslationProgress {
    data object Translating : PostTranslationProgress

    data class Translated(val text: String) : PostTranslationProgress
}

internal const val POST_TRANSLATION_FAILED_MESSAGE = "翻訳できませんでした"
