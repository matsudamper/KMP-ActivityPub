package net.matsudamper.kmp.activitypub.android

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

@RunWith(Parameterized::class)
@Category(PaparazziTestCategory::class)
class ScreenshotTest(
    private val preview: ComposablePreview<AndroidPreviewInfo>,
) {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_6,
        )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun previews(): List<ComposablePreview<AndroidPreviewInfo>> =
            AndroidComposablePreviewScanner()
                .scanPackageTrees("net.matsudamper.kmp.activitypub")
                .includePrivatePreviews()
                .getPreviews()
    }

    @Test
    fun snapshot() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.withPreviewSize(preview.previewInfo))
        paparazzi.snapshot(name = preview.methodName) {
            preview()
        }
    }
}

/**
 * Preview に widthDp / heightDp があれば、その大きさの画面で撮る。指定が無い辺は端末のまま
 */
private fun DeviceConfig.withPreviewSize(previewInfo: AndroidPreviewInfo): DeviceConfig {
    val screenWidth = if (previewInfo.widthDp > 0) previewInfo.widthDp.dpToPx(density) else screenWidth
    val screenHeight = if (previewInfo.heightDp > 0) previewInfo.heightDp.dpToPx(density) else screenHeight
    return copy(
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        orientation = if (screenWidth > screenHeight) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
    )
}

private fun Int.dpToPx(density: Density): Int = this * density.dpiValue / Density.DEFAULT_DENSITY
