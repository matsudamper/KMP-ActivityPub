package net.matsudamper.kmp.activitypub.frontend.screen.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.ui.ZoomableImage
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

@Composable
internal fun ImageViewerScreen(
    imageUrl: String,
    navigator: Navigator,
) {
    val uiState = remember(imageUrl, navigator) {
        ImageViewerScreenUiState(
            imageUrl = imageUrl,
            listener = object : ImageViewerScreenUiState.Listener {
                override fun onClickClose() {
                    navigator.back()
                }
            },
        )
    }
    ImageViewerContent(uiState = uiState)
}

@Composable
internal fun ImageViewerContent(
    uiState: ImageViewerScreenUiState,
) {
    // Web では画像を HTML の要素で重ねるので、Compose で描くボタンは画像と重ならない位置に置く
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(
                onClick = uiState.listener::onClickClose,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
            ) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = "閉じる",
                )
            }
        }
        ZoomableImage(
            imageUrl = uiState.imageUrl,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}
