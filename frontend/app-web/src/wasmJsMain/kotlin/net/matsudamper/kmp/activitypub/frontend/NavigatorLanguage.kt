package net.matsudamper.kmp.activitypub.frontend

/**
 * Compose Web は navigator.language が "ja" と完全一致したときだけ漢字の自動フォールバックに Noto Sans JP を選ぶ。
 * "ja-JP" のままだと漢字が中国語のグリフで描かれるため、Compose の起動前に日本語・韓国語は言語部分だけを返すようにする。
 * ブラウザの言語設定の変更に追従するため、読み出しのたびに元の値から変換する。
 */
internal fun normalizeNavigatorLanguageForCjkFontFallback(): Unit =
    js(
        """(() => {
            const browserLanguageGetter = Object.getOwnPropertyDescriptor(Navigator.prototype, 'language').get;
            Object.defineProperty(navigator, 'language', {
                configurable: true,
                get() {
                    const language = browserLanguageGetter.call(this);
                    const primaryLanguage = language.split('-')[0];
                    return primaryLanguage === 'ja' || primaryLanguage === 'ko' ? primaryLanguage : language;
                },
            });
        })()""",
    )
