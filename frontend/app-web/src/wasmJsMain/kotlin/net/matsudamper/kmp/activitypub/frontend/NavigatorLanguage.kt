package net.matsudamper.kmp.activitypub.frontend

/**
 * Compose Web は navigator.language が "ja" と完全一致したときだけ漢字の自動フォールバックに Noto Sans JP を選ぶ。
 * "ja-JP" のままだと漢字が中国語のグリフで描かれるため、Compose の起動前に日本語・韓国語は言語部分だけにする。
 */
internal fun normalizeNavigatorLanguageForCjkFontFallback(): Unit =
    js(
        """(() => {
            const language = navigator.language;
            const primaryLanguage = language.split('-')[0];
            if (language === primaryLanguage || (primaryLanguage !== 'ja' && primaryLanguage !== 'ko')) return;
            Object.defineProperty(navigator, 'language', { configurable: true, get: () => primaryLanguage });
        })()""",
    )
