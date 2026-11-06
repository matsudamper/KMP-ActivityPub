package net.matsudamper.kmp.activitypub.frontend.screen.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import net.matsudamper.kmp.activitypub.frontend.logic.theme.ThemeMode

@Composable
fun ThemeMode.isDarkTheme(): Boolean =
    when (this) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

@Composable
fun AppTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val fontFamily = rememberAppFontFamily()
    val typography = remember(fontFamily) { Typography().withFontFamily(fontFamily).enlargedToMastodonScale() }
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        typography = typography,
        content = content,
    )
}

private fun Typography.withFontFamily(fontFamily: FontFamily): Typography =
    Typography(
        displayLarge = displayLarge.copy(fontFamily = fontFamily),
        displayMedium = displayMedium.copy(fontFamily = fontFamily),
        displaySmall = displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = titleLarge.copy(fontFamily = fontFamily),
        titleMedium = titleMedium.copy(fontFamily = fontFamily),
        titleSmall = titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily),
    )

/**
 * M3 の既定サイズは Mastodon Web（本文 15px）より一回り小さいので、全スタイルを 1sp 大きくする。
 * 行の詰まりを避けるため行間は 2sp 広げる。
 */
private fun Typography.enlargedToMastodonScale(): Typography =
    Typography(
        displayLarge = displayLarge.enlarged(),
        displayMedium = displayMedium.enlarged(),
        displaySmall = displaySmall.enlarged(),
        headlineLarge = headlineLarge.enlarged(),
        headlineMedium = headlineMedium.enlarged(),
        headlineSmall = headlineSmall.enlarged(),
        titleLarge = titleLarge.enlarged(),
        titleMedium = titleMedium.enlarged(),
        titleSmall = titleSmall.enlarged(),
        bodyLarge = bodyLarge.enlarged(),
        bodyMedium = bodyMedium.enlarged(),
        bodySmall = bodySmall.enlarged(),
        labelLarge = labelLarge.enlarged(),
        labelMedium = labelMedium.enlarged(),
        labelSmall = labelSmall.enlarged(),
    )

private fun TextStyle.enlarged(): TextStyle =
    copy(
        fontSize = (fontSize.value + 1).sp,
        lineHeight = (lineHeight.value + 2).sp,
    )
