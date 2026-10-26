package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

/**
 * 丸く切り抜いたプロフィール画像。[iconUrl] が null か画像を取れなければ人の形のアイコンを出す
 */
@Composable
internal fun ProfileIcon(
    iconUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        // 取れなかったときに背景だけが残らないよう、人の形のアイコンに戻す
        var loadFailed by remember(iconUrl) { mutableStateOf(false) }
        if (iconUrl != null && loadFailed.not()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = AppIcons.Person,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
