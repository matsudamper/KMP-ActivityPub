package net.matsudamper.kmp.activitypub.frontend.ui

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

@Composable
internal actual fun VideoPlayerDialog(
    videoUrl: String,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    var closeButtonVisible by remember { mutableStateOf(true) }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                closeButtonVisible = visibility == View.VISIBLE
                            },
                        )
                    }
                },
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            )
            AnimatedVisibility(
                visible = closeButtonVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp),
            ) {
                IconButton(
                    onClick = onDismissRequest,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF333333),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(imageVector = AppIcons.Close, contentDescription = "閉じる")
                }
            }
        }
    }
}
