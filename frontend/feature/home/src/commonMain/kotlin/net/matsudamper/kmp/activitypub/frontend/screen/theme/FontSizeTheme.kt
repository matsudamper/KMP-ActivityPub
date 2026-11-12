package net.matsudamper.kmp.activitypub.frontend.screen.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

enum class AppFontSize(
    internal val fontSizeDeltaSp: Float,
) {
    Small(fontSizeDeltaSp = -2f),
    Medium(fontSizeDeltaSp = 0f),
    Large(fontSizeDeltaSp = 2f),
}

/**
 * 外側の [MaterialTheme] の文字の大きさだけを変えて、中身を描く。
 * 色や形はそのまま引き継ぐ
 */
@Composable
fun FontSizeTheme(
    fontSize: AppFontSize,
    content: @Composable () -> Unit,
) {
    val outerTypography = MaterialTheme.typography
    val typography = remember(outerTypography, fontSize) { outerTypography.shifted(fontSize.fontSizeDeltaSp) }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = typography,
        content = content,
    )
}

private fun Typography.shifted(deltaSp: Float): Typography =
    Typography(
        displayLarge = displayLarge.shifted(deltaSp),
        displayMedium = displayMedium.shifted(deltaSp),
        displaySmall = displaySmall.shifted(deltaSp),
        headlineLarge = headlineLarge.shifted(deltaSp),
        headlineMedium = headlineMedium.shifted(deltaSp),
        headlineSmall = headlineSmall.shifted(deltaSp),
        titleLarge = titleLarge.shifted(deltaSp),
        titleMedium = titleMedium.shifted(deltaSp),
        titleSmall = titleSmall.shifted(deltaSp),
        bodyLarge = bodyLarge.shifted(deltaSp),
        bodyMedium = bodyMedium.shifted(deltaSp),
        bodySmall = bodySmall.shifted(deltaSp),
        labelLarge = labelLarge.shifted(deltaSp),
        labelMedium = labelMedium.shifted(deltaSp),
        labelSmall = labelSmall.shifted(deltaSp),
    )

private fun TextStyle.shifted(deltaSp: Float): TextStyle =
    copy(
        fontSize = (fontSize.value + deltaSp).sp,
        lineHeight = (lineHeight.value + deltaSp).sp,
    )
