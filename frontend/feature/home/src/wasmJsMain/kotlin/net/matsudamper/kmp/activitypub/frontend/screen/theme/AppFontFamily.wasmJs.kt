package net.matsudamper.kmp.activitypub.frontend.screen.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import kotlinx.coroutines.CancellationException
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes

/**
 * Compose Web は OS のフォントを使えず、漢字やかなは描画時に外部から補完フォントを取りに行くため、後から表示される。
 * 同梱した Noto Sans JP を読み込み、読めた太さから順に使う
 */
@Composable
internal actual fun rememberAppFontFamily(): FontFamily {
    val fontFamilyState = remember { AppFontFamilyState() }
    LaunchedEffect(fontFamilyState) {
        fontFamilyState.load()
    }
    return fontFamilyState.fontFamily
}

@Stable
private class AppFontFamilyState {
    var fontFamily: FontFamily by mutableStateOf(FontFamily.Default)
        private set

    suspend fun load() {
        val httpClient = HttpClient(Js)
        try {
            BUNDLED_FONTS.fold(listOf<Font>()) { loadedFonts, bundledFont ->
                val font = download(httpClient = httpClient, bundledFont = bundledFont) ?: return@fold loadedFonts
                val nextLoadedFonts = loadedFonts + font
                fontFamily = FontFamily(nextLoadedFonts)
                nextLoadedFonts
            }
        } finally {
            httpClient.close()
        }
    }

    private suspend fun download(
        httpClient: HttpClient,
        bundledFont: BundledFont,
    ): Font? =
        try {
            val bytes = httpClient.get("/fonts/${bundledFont.fileName}").readRawBytes()
            Font(
                identity = bundledFont.fileName,
                data = bytes,
                weight = bundledFont.weight,
                style = FontStyle.Normal,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
}

private data class BundledFont(
    val fileName: String,
    val weight: FontWeight,
)

// 1 ファイルが大きいので、よく使う太さから読み込む
private val BUNDLED_FONTS: List<BundledFont> =
    listOf(
        BundledFont("NotoSansJP-Regular.ttf", FontWeight.Normal),
        BundledFont("NotoSansJP-Bold.ttf", FontWeight.Bold),
        BundledFont("NotoSansJP-Medium.ttf", FontWeight.Medium),
        BundledFont("NotoSansJP-SemiBold.ttf", FontWeight.SemiBold),
        BundledFont("NotoSansJP-ExtraBold.ttf", FontWeight.ExtraBold),
        BundledFont("NotoSansJP-Black.ttf", FontWeight.Black),
        BundledFont("NotoSansJP-Light.ttf", FontWeight.Light),
        BundledFont("NotoSansJP-ExtraLight.ttf", FontWeight.ExtraLight),
        BundledFont("NotoSansJP-Thin.ttf", FontWeight.Thin),
    )
